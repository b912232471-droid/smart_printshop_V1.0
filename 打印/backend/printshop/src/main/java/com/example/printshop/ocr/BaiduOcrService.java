package com.example.printshop.ocr;

import com.example.printshop.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BaiduOcrService {
    private static final Logger log = LoggerFactory.getLogger(BaiduOcrService.class);
    private static final long MAX_IMAGE_BYTES = 4L * 1024 * 1024;
    private static final int MAX_IMAGE_WIDTH = 4096;
    private static final int MAX_IMAGE_HEIGHT = 4096;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String secretKey;
    private final String oauthUrl;
    private final String ocrUrl;
    private final String uploadDirProperty;
    private final String pdfFontPath;
    private final int timeoutMs;
    private final AtomicReference<Token> token = new AtomicReference<>();
    private final AtomicReference<Boolean> pdfFontProbe = new AtomicReference<>();

    public BaiduOcrService(RestTemplateBuilder restTemplateBuilder,
                           ObjectMapper objectMapper,
                           @Value("${baidu.ocr.api-key:}") String apiKey,
                           @Value("${baidu.ocr.secret-key:}") String secretKey,
                           @Value("${baidu.ocr.oauth-url:https://aip.baidubce.com/oauth/2.0/token}") String oauthUrl,
                           @Value("${baidu.ocr.endpoint:https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic}") String ocrUrl,
                           @Value("${printshop.file.upload-dir:}") String uploadDirProperty,
                           @Value("${baidu.ocr.pdf-font-path:}") String pdfFontPath,
                           @Value("${baidu.ocr.timeout-ms:60000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.oauthUrl = oauthUrl;
        this.ocrUrl = ocrUrl;
        this.uploadDirProperty = uploadDirProperty;
        this.pdfFontPath = pdfFontPath == null ? "" : pdfFontPath.trim();
        this.timeoutMs = timeoutMs;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10000)))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !secretKey.isBlank();
    }

    public boolean isPdfFontAvailable() {
        Boolean cached = pdfFontProbe.get();
        if (cached != null) {
            return cached;
        }
        boolean available = probePdfFont();
        pdfFontProbe.set(available);
        return available;
    }

    private boolean probePdfFont() {
        for (String candidate : fontCandidates()) {
            File file = new File(candidate);
            if (!file.isFile()) {
                continue;
            }
            try {
                if (candidate.toLowerCase().endsWith(".ttc")) {
                    try (TrueTypeCollection collection = new TrueTypeCollection(file)) {
                        List<TrueTypeFont> fonts = new ArrayList<>();
                        collection.processAllFonts(fonts::add);
                        if (fonts.stream().anyMatch(this::hasGlyfOutline)) {
                            return true;
                        }
                    }
                } else {
                    return true;
                }
            } catch (IOException e) {
                log.warn("OCR PDF font probe failed: {}", candidate, e);
            }
        }
        return false;
    }

    public String recognize(MultipartFile file) {
        if (!isConfigured()) {
            throw ApiException.serviceUnavailable("百度 OCR 尚未配置，请联系管理员");
        }
        validateImage(file);
        try {
            return recognize(file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("读取图片失败", e);
        }
    }

    public String recognize(byte[] bytes) {
        try {
            String accessToken = accessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("image", Base64.getEncoder().encodeToString(bytes));
            body.add("detect_direction", "true");
            body.add("probability", "true");
            String url = ocrUrl + "?access_token=" + accessToken;
            JsonNode result = objectMapper.readTree(restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class));
            if (result.has("error_code")) {
                throw ApiException.serviceUnavailable("百度 OCR 调用失败：" + result.path("error_msg").asText("unknown error"));
            }
            List<String> lines = new ArrayList<>();
            result.path("words_result").forEach(item -> {
                String words = item.path("words").asText("").trim();
                if (!words.isBlank()) {
                    lines.add(words);
                }
            });
            if (lines.isEmpty()) {
                throw ApiException.badRequest("未识别到图片文字");
            }
            return String.join("\n", lines);
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException | IOException e) {
            throw new RuntimeException("百度 OCR 网络调用失败", e);
        }
    }

    public Path createDocx(String text, Path output) {
        try {
            Files.createDirectories(output.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
                put(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                        </Types>
                        """);
                put(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                        </Relationships>
                        """);
                StringBuilder paragraphs = new StringBuilder();
                for (String line : text.split("\\R", -1)) {
                    paragraphs.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                            .append(xml(line)).append("</w:t></w:r></w:p>");
                }
                put(zip, "word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:body>" + paragraphs + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>");
            }
            return output;
        } catch (IOException e) {
            throw new RuntimeException("生成 Word 文档失败", e);
        }
    }

    public Path createPdf(String text, Path output) {
        try {
            Files.createDirectories(output.getParent());
            try (PDDocument document = new PDDocument();
                 OcrPdfFont fontResource = loadPdfFont(document)) {
                PDFont font = fontResource.font();
                List<String> lines = wrap(text, font, 12, 500);
                float margin = 48;
                float lineHeight = 18;
                int linesPerPage = 48;
                for (int offset = 0; offset < lines.size(); offset += linesPerPage) {
                    PDPage page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(font, 12);
                        content.setLeading(lineHeight);
                        content.newLineAtOffset(margin, page.getMediaBox().getHeight() - margin);
                        for (int i = offset; i < Math.min(offset + linesPerPage, lines.size()); i++) {
                            content.showText(lines.get(i));
                            content.newLine();
                        }
                        content.endText();
                    }
                }
                if (lines.isEmpty()) {
                    document.addPage(new PDPage(PDRectangle.A4));
                }
                document.save(output.toFile());
            }
            return output;
        } catch (ApiException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("OCR PDF glyph encoding failed", e);
            throw ApiException.serviceUnavailable("生成 PDF 失败：当前字体缺少文本所需的字形，请检查服务器中文字体配置");
        } catch (IOException e) {
            throw new RuntimeException("生成 PDF 文档失败", e);
        }
    }

    public Path outputDir(Integer ownerId) {
        Path root = resolveUploadDir().resolve("ocr").resolve(String.valueOf(ownerId)).normalize();
        if (!root.startsWith(resolveUploadDir())) {
            throw ApiException.badRequest("invalid OCR output path");
        }
        return root;
    }

    public Path resolveDownload(Integer ownerId, String tokenValue, String format) {
        if (ownerId == null || tokenValue == null || !tokenValue.matches("[a-fA-F0-9-]{20,}")) {
            throw ApiException.notFound("文件不存在");
        }
        if (!"docx".equals(format) && !"pdf".equals(format)) {
            throw ApiException.notFound("文件不存在");
        }
        Path file = outputDir(ownerId).resolve(tokenValue + "." + format).normalize();
        if (!file.startsWith(outputDir(ownerId)) || !Files.isRegularFile(file)) {
            throw ApiException.notFound("文件不存在");
        }
        return file;
    }

    private String accessToken() throws IOException {
        Token cached = token.get();
        if (cached != null && cached.expiresAt > Instant.now().getEpochSecond() + 60) {
            return cached.value;
        }
        synchronized (token) {
            cached = token.get();
            if (cached != null && cached.expiresAt > Instant.now().getEpochSecond() + 60) {
                return cached.value;
            }
            String url = oauthUrl + "?grant_type=client_credentials&client_id=" + encode(apiKey) + "&client_secret=" + encode(secretKey);
            String raw = restTemplate.postForObject(url, null, String.class);
            JsonNode result = objectMapper.readTree(raw);
            String value = result.path("access_token").asText("");
            if (value.isBlank()) {
                throw ApiException.serviceUnavailable("百度 OCR 鉴权失败");
            }
            long expiresIn = result.path("expires_in").asLong(1800);
            token.set(new Token(value, Instant.now().getEpochSecond() + expiresIn));
            return value;
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请上传图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw ApiException.badRequest("图片不能超过 4MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".bmp"))) {
            throw ApiException.badRequest("仅支持 JPG、PNG、BMP 图片");
        }
        try (InputStream input = file.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() < 15 || image.getHeight() < 15) {
                throw ApiException.badRequest("图片尺寸无效");
            }
            if (image.getWidth() > MAX_IMAGE_WIDTH || image.getHeight() > MAX_IMAGE_HEIGHT) {
                throw ApiException.badRequest("图片尺寸不能超过 4096×4096");
            }
        } catch (IOException e) {
            throw ApiException.badRequest("图片内容无法读取");
        }
    }

    private OcrPdfFont loadPdfFont(PDDocument document) {
        for (String candidate : fontCandidates()) {
            File file = new File(candidate);
            if (!file.isFile()) {
                continue;
            }
            try {
                if (candidate.toLowerCase().endsWith(".ttc")) {
                    return loadTtcFont(document, file);
                }
                return new OcrPdfFont(PDType0Font.load(document, file), null);
            } catch (Exception e) {
                log.warn("OCR PDF font load failed: {}", candidate, e);
            }
        }
        throw ApiException.serviceUnavailable("服务器缺少可用中文字体，无法生成 PDF，请联系管理员配置 BAIDU_OCR_PDF_FONT_PATH");
    }

    private OcrPdfFont loadTtcFont(PDDocument document, File file) throws IOException {
        TrueTypeCollection collection = new TrueTypeCollection(file);
        try {
            TrueTypeFont ttf = pickCollectionFont(collection);
            PDFont font = PDType0Font.load(document, ttf, true);
            log.info("OCR PDF font loaded: {} from {}", ttf.getName(), file.getName());
            return new OcrPdfFont(font, collection);
        } catch (RuntimeException | IOException e) {
            try {
                collection.close();
            } catch (IOException closeError) {
                log.warn("Failed to close TTC font collection: {}", file.getName(), closeError);
            }
            throw e;
        }
    }

    private TrueTypeFont pickCollectionFont(TrueTypeCollection collection) throws IOException {
        List<TrueTypeFont> fonts = new ArrayList<>();
        collection.processAllFonts(fonts::add);
        if (fonts.isEmpty()) {
            throw new IOException("TTC 字体集合中没有任何字体");
        }
        TrueTypeFont firstEmbeddable = null;
        for (TrueTypeFont font : fonts) {
            if (!hasGlyfOutline(font)) {
                continue;
            }
            if (firstEmbeddable == null) {
                firstEmbeddable = font;
            }
            if (isSimplifiedChineseVariant(font)) {
                return font;
            }
        }
        if (firstEmbeddable != null) {
            return firstEmbeddable;
        }
        throw new IOException("TTC 字体集合中没有 glyf outline 的 TrueType 字体（OpenType/CFF 字体无法嵌入 PDF）");
    }

    private boolean hasGlyfOutline(TrueTypeFont font) {
        try {
            return font.getGlyph() != null;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isSimplifiedChineseVariant(TrueTypeFont font) {
        try {
            String name = font.getName();
            return name != null && name.endsWith("sc-Regular");
        } catch (IOException e) {
            return false;
        }
    }

    private List<String> fontCandidates() {
        List<String> candidates = new ArrayList<>();
        if (!pdfFontPath.isBlank()) candidates.add(pdfFontPath);
        candidates.add("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc");
        candidates.add("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc");
        candidates.add("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");
        candidates.add("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc");
        candidates.add("C:/Windows/Fonts/msyh.ttc");
        return candidates;
    }

    private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        for (String sourceLine : text.split("\\R", -1)) {
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < sourceLine.length(); i++) {
                String candidate = current + String.valueOf(sourceLine.charAt(i));
                if (font.getStringWidth(candidate) / 1000 * fontSize > maxWidth && !current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                current.append(sourceLine.charAt(i));
            }
            result.add(current.toString());
        }
        return result;
    }

    private void put(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Path resolveUploadDir() {
        if (uploadDirProperty != null && !uploadDirProperty.isBlank()) {
            return Paths.get(uploadDirProperty).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir"), "files").toAbsolutePath().normalize();
    }

    private record Token(String value, long expiresAt) {
    }

    private record OcrPdfFont(PDFont font, TrueTypeCollection collection) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (collection != null) {
                collection.close();
            }
        }
    }
}




