package com.example.printshop.controller;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.FileInfo;
import com.example.printshop.entity.OrderInfo;
import com.example.printshop.security.AuthContext;
import com.example.printshop.security.AuthPrincipal;
import com.example.printshop.security.FileScanService;
import com.example.printshop.security.ImageContentModerationService;
import com.example.printshop.service.FileInfoService;
import com.example.printshop.service.OrderInfoService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api/file")
public class FileInfoController {
    private static final int ZIP_BUFFER_SIZE = 8192;
    private static final int MAX_OPENXML_METADATA_SCAN_BYTES = 1024 * 1024;
    private static final Set<String> PRINT_FILE_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif", ".txt"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif"
    );

    private final FileInfoService fileService;
    private final OrderInfoService orderInfoService;
    private final String uploadDirProperty;
    private final FileScanService fileScanService;
    private final ImageContentModerationService imageContentModerationService;
    private final int maxOpenXmlEntries;
    private final long maxOpenXmlEntryUncompressedBytes;
    private final long maxOpenXmlTotalUncompressedBytes;
    private final int maxPdfPages;
    private final boolean rejectEncryptedPdf;
    private final int maxPdfObjects;
    private final int maxPdfPageContentStreams;
    private final long maxPdfPageContentBytes;
    private final long maxPdfTotalContentBytes;
    private final int maxImageWidth;
    private final int maxImageHeight;
    private final long maxImagePixels;

    public FileInfoController(FileInfoService fileService,
                              OrderInfoService orderInfoService,
                              @Value("${printshop.file.upload-dir:}") String uploadDirProperty,
                              FileScanService fileScanService,
                              ImageContentModerationService imageContentModerationService,
                              @Value("${printshop.file.openxml.max-entries:2000}") int maxOpenXmlEntries,
                              @Value("${printshop.file.openxml.max-entry-uncompressed-bytes:20971520}") long maxOpenXmlEntryUncompressedBytes,
                              @Value("${printshop.file.openxml.max-total-uncompressed-bytes:104857600}") long maxOpenXmlTotalUncompressedBytes,
                              @Value("${printshop.file.pdf.max-pages:200}") int maxPdfPages,
                              @Value("${printshop.file.pdf.reject-encrypted:true}") boolean rejectEncryptedPdf,
                              @Value("${printshop.file.pdf.max-objects:10000}") int maxPdfObjects,
                              @Value("${printshop.file.pdf.max-page-content-streams:64}") int maxPdfPageContentStreams,
                              @Value("${printshop.file.pdf.max-page-content-bytes:2097152}") long maxPdfPageContentBytes,
                              @Value("${printshop.file.pdf.max-total-content-bytes:20971520}") long maxPdfTotalContentBytes,
                              @Value("${printshop.file.image.max-width:10000}") int maxImageWidth,
                              @Value("${printshop.file.image.max-height:10000}") int maxImageHeight,
                              @Value("${printshop.file.image.max-pixels:40000000}") long maxImagePixels) {
        this.fileService = fileService;
        this.orderInfoService = orderInfoService;
        this.uploadDirProperty = uploadDirProperty;
        this.fileScanService = fileScanService;
        this.imageContentModerationService = imageContentModerationService;
        this.maxOpenXmlEntries = maxOpenXmlEntries;
        this.maxOpenXmlEntryUncompressedBytes = maxOpenXmlEntryUncompressedBytes;
        this.maxOpenXmlTotalUncompressedBytes = maxOpenXmlTotalUncompressedBytes;
        this.maxPdfPages = maxPdfPages;
        this.rejectEncryptedPdf = rejectEncryptedPdf;
        this.maxPdfObjects = maxPdfObjects;
        this.maxPdfPageContentStreams = maxPdfPageContentStreams;
        this.maxPdfPageContentBytes = maxPdfPageContentBytes;
        this.maxPdfTotalContentBytes = maxPdfTotalContentBytes;
        this.maxImageWidth = maxImageWidth;
        this.maxImageHeight = maxImageHeight;
        this.maxImagePixels = maxImagePixels;
    }

    @GetMapping("/")
    public List<FileInfo> getAll() {
        AuthContext.requireAdmin();
        return fileService.getAll().stream()
                .map(this::publicFile)
                .toList();
    }

    @GetMapping("/{id}")
    public FileInfo getById(@PathVariable Integer id) {
        return publicFile(requireFileAccess(id));
    }

    @GetMapping("/order/{orderId}")
    public List<FileInfo> getByOrder(@PathVariable Integer orderId) {
        requireOrderAccess(orderId);
        return fileService.getByOrderId(orderId).stream()
                .map(this::publicFile)
                .toList();
    }

    @PostMapping("/upload")
    public Map<String, Object> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("orderId") Integer orderId,
            @RequestParam(value = "originalFileName", required = false) String originalFileName) {
        requireOrderAccess(orderId);
        validateFile(file, PRINT_FILE_EXTENSIONS);

        Path dest = null;
        try {
            Path uploadDir = resolveUploadDir();
            Files.createDirectories(uploadDir);

            String displayName = safeDisplayName(originalFileName, file.getOriginalFilename());
            String suffix = extension(displayName);
            String savedFilename = "order_" + orderId + "_" + timestamp() + "_" + UUID.randomUUID() + suffix;
            dest = uploadDir.resolve(savedFilename).normalize();
            if (!dest.startsWith(uploadDir)) {
                throw ApiException.badRequest("invalid file path");
            }
            file.transferTo(dest);

            FileInfo fileInfo = new FileInfo();
            fileInfo.setOrderId(orderId);
            fileInfo.setFileName(displayName);
            fileInfo.setFileUrl("/files/" + savedFilename);
            fileInfo.setUploadTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            int rows = fileService.add(fileInfo);
            if (rows <= 0) {
                deleteIfExists(dest);
                throw ApiException.serviceUnavailable("file metadata save failed");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "上传成功");
            result.put("fileUrl", "/api/file/download/" + fileInfo.getId());
            result.put("fileId", fileInfo.getId());
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception e) {
            deleteIfExists(dest);
            throw new RuntimeException("upload failed", e);
        }
    }

    @DeleteMapping("/{id}")
    public int delete(@PathVariable Integer id) {
        AuthContext.requireAdmin();
        FileInfo fileInfo = fileService.getById(id);
        if (fileInfo == null) {
            throw ApiException.notFound("文件不存在");
        }
        Path storedFile = storedFilePath(fileInfo);
        deleteIfExists(storedFile);
        return fileService.delete(id);
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Integer fileId) {
        FileInfo fileInfo = requireFileAccess(fileId);
        try {
            Path file = storedFilePath(fileInfo);
            if (!Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String encodedFilename = URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(resource);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("download failed", e);
        }
    }

    @PostMapping("/avatar")
    public Map<String, Object> uploadAvatar(@RequestParam("file") MultipartFile file) {
        AuthContext.requireUser();
        return uploadPublicImage(file, "avatars", "avatar_", "avatarUrl");
    }

    @PostMapping("/store-image")
    public Map<String, Object> uploadStoreImage(@RequestParam("file") MultipartFile file) {
        AuthContext.requireAdmin();
        return uploadPublicImage(file, "stores", "store_", "imageUrl");
    }

    private Map<String, Object> uploadPublicImage(MultipartFile file, String folder, String prefix, String urlKey) {
        validateFile(file, IMAGE_EXTENSIONS);
        try {
            Path dir = resolveUploadDir().resolve(folder).normalize();
            Files.createDirectories(dir);
            String displayName = safeDisplayName(null, file.getOriginalFilename());
            String suffix = extension(displayName);
            String filename = prefix + System.currentTimeMillis() + "_" + UUID.randomUUID() + suffix;
            Path dest = dir.resolve(filename).normalize();
            if (!dest.startsWith(dir)) {
                throw ApiException.badRequest("invalid image path");
            }
            file.transferTo(dest);

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "上传成功");
            result.put(urlKey, "/api/public-files/" + folder + "/" + filename);
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception e) {
            throw new RuntimeException("image upload failed", e);
        }
    }

    private void validateFile(MultipartFile file, Set<String> allowedExtensions) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("文件为空");
        }
        String name = safeDisplayName(null, file.getOriginalFilename());
        String ext = extension(name);
        if (!allowedExtensions.contains(ext)) {
            throw ApiException.badRequest("不支持的文件类型");
        }
        if (!contentMatchesExtension(file, ext)) {
            throw ApiException.badRequest("文件内容与扩展名不匹配");
        }
        if (IMAGE_EXTENSIONS.contains(ext)) {
            imageContentModerationService.moderate(file);
        }
        fileScanService.scan(file);
    }

    private FileInfo requireFileAccess(Integer fileId) {
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null) {
            throw ApiException.notFound("文件不存在");
        }
        requireOrderAccess(fileInfo.getOrderId());
        return fileInfo;
    }

    private OrderInfo requireOrderAccess(Integer orderId) {
        OrderInfo order = orderInfoService.getOrderById(orderId);
        if (order == null) {
            throw ApiException.notFound("订单不存在");
        }
        AuthPrincipal principal = AuthContext.get();
        if (principal.isAdmin()) {
            return order;
        }
        if (principal.isUser() && order.getUserId() != null && order.getUserId().equals(principal.getId())) {
            return order;
        }
        throw ApiException.forbidden("cannot access another user's file");
    }

    private Path resolveUploadDir() {
        String configured = uploadDirProperty;
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        String productionDir = "/www/wwwroot/printshop/files/";
        if (new File(productionDir).exists()) {
            return Paths.get(productionDir).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir"), "files").toAbsolutePath().normalize();
    }

    private FileInfo publicFile(FileInfo source) {
        if (source == null) {
            return null;
        }
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(source.getId());
        fileInfo.setOrderId(source.getOrderId());
        fileInfo.setFileName(source.getFileName());
        fileInfo.setUploadTime(source.getUploadTime());
        if (source.getId() != null) {
            fileInfo.setFileUrl("/api/file/download/" + source.getId());
        }
        return fileInfo;
    }

    private String safeDisplayName(String preferred, String fallback) {
        String name = cleanDisplayName(preferred);
        if (name == null) {
            name = cleanDisplayName(fallback);
        }
        if (name == null) {
            name = "file";
        }
        if (name.length() > 180) {
            String ext = extension(name);
            name = name.substring(0, Math.min(160, name.length() - ext.length())) + ext;
        }
        return name;
    }

    private String cleanDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String name = value;
        name = name.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t\\x00]", "").trim();
        if (name.isBlank()) {
            return null;
        }
        name = name.replaceAll("[<>:\"|?*]", "_");
        return name;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private boolean contentMatchesExtension(MultipartFile file, String ext) {
        byte[] header = readHeader(file);
        return switch (ext) {
            case ".pdf" -> isPdf(file, header);
            case ".jpg", ".jpeg" -> hasMagic(header, 0xFF, 0xD8, 0xFF) && hasAcceptableImageDimensions(file, ext);
            case ".png" -> hasMagic(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) && hasAcceptableImageDimensions(file, ext);
            case ".gif" -> (startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII))) && hasAcceptableImageDimensions(file, ext);
            case ".bmp" -> startsWith(header, "BM".getBytes(StandardCharsets.US_ASCII)) && hasAcceptableImageDimensions(file, ext);
            case ".webp" -> isWebp(header) && hasAcceptableImageDimensions(file, ext);
            case ".doc", ".xls", ".ppt" -> hasMagic(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case ".docx" -> isOpenXml(file, "word/");
            case ".xlsx" -> isOpenXml(file, "xl/");
            case ".pptx" -> isOpenXml(file, "ppt/");
            case ".txt" -> isPlainText(header);
            default -> false;
        };
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(16);
        } catch (IOException e) {
            throw ApiException.badRequest("无法读取文件内容");
        }
    }

    private boolean isPdf(MultipartFile file, byte[] header) {
        if (!startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (rejectEncryptedPdf && document.isEncrypted()) {
                return false;
            }
            int pages = document.getNumberOfPages();
            return pages > 0 && pages <= maxPdfPages && hasAcceptablePdfComplexity(document);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean hasAcceptablePdfComplexity(PDDocument document) throws IOException {
        if (maxPdfObjects > 0 && document.getDocument().getXrefTable().size() > maxPdfObjects) {
            return false;
        }

        long totalContentBytes = 0;
        for (PDPage page : document.getPages()) {
            int contentStreams = 0;
            long pageContentBytes = 0;
            Iterator<PDStream> streams = page.getContentStreams();
            while (streams.hasNext()) {
                contentStreams++;
                if (maxPdfPageContentStreams > 0 && contentStreams > maxPdfPageContentStreams) {
                    return false;
                }
                long remainingPageBytes = maxPdfPageContentBytes > 0
                        ? maxPdfPageContentBytes - pageContentBytes
                        : Long.MAX_VALUE;
                long remainingTotalBytes = maxPdfTotalContentBytes > 0
                        ? maxPdfTotalContentBytes - totalContentBytes
                        : Long.MAX_VALUE;
                long streamBytes = countPdfContentStreamBytes(
                        streams.next(),
                        Math.min(remainingPageBytes, remainingTotalBytes)
                );
                pageContentBytes += streamBytes;
                totalContentBytes += streamBytes;
                if ((maxPdfPageContentBytes > 0 && pageContentBytes > maxPdfPageContentBytes)
                        || (maxPdfTotalContentBytes > 0 && totalContentBytes > maxPdfTotalContentBytes)) {
                    return false;
                }
            }
        }
        return true;
    }

    private long countPdfContentStreamBytes(PDStream stream, long allowedBytes) throws IOException {
        long bytes = 0;
        byte[] buffer = new byte[ZIP_BUFFER_SIZE];
        try (InputStream input = stream.createInputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes += read;
                if (allowedBytes >= 0 && bytes > allowedBytes) {
                    return bytes;
                }
            }
        }
        return bytes;
    }

    private boolean hasAcceptableImageDimensions(MultipartFile file, String ext) {
        try {
            ImageSize size = ".webp".equals(ext) ? readWebpSize(file) : readImageSize(file);
            long pixels = (long) size.width() * size.height();
            return size.width() > 0
                    && size.height() > 0
                    && size.width() <= maxImageWidth
                    && size.height() <= maxImageHeight
                    && pixels <= maxImagePixels;
        } catch (Exception e) {
            return false;
        }
    }

    private ImageSize readImageSize(MultipartFile file) throws IOException {
        ImageIO.setUseCache(false);
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageInput == null) {
                throw new IOException("unsupported image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IOException("unsupported image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                return new ImageSize(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private ImageSize readWebpSize(MultipartFile file) throws IOException {
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes(64);
        }
        if (bytes.length < 30 || !isWebp(bytes)) {
            throw new IOException("invalid webp");
        }
        String chunk = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8X".equals(chunk)) {
            int width = 1 + littleEndian24(bytes, 24);
            int height = 1 + littleEndian24(bytes, 27);
            return new ImageSize(width, height);
        }
        if ("VP8L".equals(chunk) && bytes[20] == 0x2F) {
            int b1 = bytes[21] & 0xFF;
            int b2 = bytes[22] & 0xFF;
            int b3 = bytes[23] & 0xFF;
            int b4 = bytes[24] & 0xFF;
            int width = 1 + (((b2 & 0x3F) << 8) | b1);
            int height = 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
            return new ImageSize(width, height);
        }
        if ("VP8 ".equals(chunk)
                && (bytes[23] & 0xFF) == 0x9D
                && (bytes[24] & 0xFF) == 0x01
                && (bytes[25] & 0xFF) == 0x2A) {
            int width = littleEndian16(bytes, 26) & 0x3FFF;
            int height = littleEndian16(bytes, 28) & 0x3FFF;
            return new ImageSize(width, height);
        }
        throw new IOException("unsupported webp");
    }

    private int littleEndian16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private int littleEndian24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16);
    }

    private boolean isOpenXml(MultipartFile file, String requiredPrefix) {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            boolean hasContentTypes = false;
            boolean hasRequiredDir = false;
            int entries = 0;
            long totalUncompressedBytes = 0;
            byte[] buffer = new byte[ZIP_BUFFER_SIZE];
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > maxOpenXmlEntries) {
                    return false;
                }
                String name = entry.getName();
                if (isUnsafeZipEntryName(name)) {
                    return false;
                }
                if (isActiveOpenXmlEntryName(name)) {
                    return false;
                }
                if ("[Content_Types].xml".equals(name)) {
                    hasContentTypes = true;
                }
                if (name.startsWith(requiredPrefix)) {
                    hasRequiredDir = true;
                }
                ByteArrayOutputStream metadata = shouldInspectOpenXmlMetadata(name) ? new ByteArrayOutputStream() : null;
                if (!entry.isDirectory()) {
                    long entryBytes = 0;
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        entryBytes += read;
                        totalUncompressedBytes += read;
                        if (entryBytes > maxOpenXmlEntryUncompressedBytes
                                || totalUncompressedBytes > maxOpenXmlTotalUncompressedBytes) {
                            return false;
                        }
                        if (metadata != null) {
                            if (entryBytes > MAX_OPENXML_METADATA_SCAN_BYTES) {
                                return false;
                            }
                            metadata.write(buffer, 0, read);
                        }
                    }
                }
                if (metadata != null && hasUnsafeOpenXmlMetadata(metadata.toString(StandardCharsets.UTF_8))) {
                    return false;
                }
            }
            return hasContentTypes && hasRequiredDir;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isUnsafeZipEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                || name.contains("\\") || name.contains(":")) {
            return true;
        }
        Path normalized = Paths.get(name).normalize();
        return normalized.isAbsolute() || normalized.startsWith("..") || name.contains("../") || name.contains("..\\");
    }

    private boolean isActiveOpenXmlEntryName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("\\", "/");
        return normalized.endsWith("vbaproject.bin")
                || normalized.contains("/vba")
                || normalized.contains("/activex/")
                || normalized.contains("/embeddings/")
                || normalized.contains("/oleobjects/")
                || normalized.contains("/macrosheets/");
    }

    private boolean shouldInspectOpenXmlMetadata(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return "[content_types].xml".equals(normalized) || normalized.endsWith(".rels");
    }

    private boolean hasUnsafeOpenXmlMetadata(String metadata) {
        String compact = metadata.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return compact.contains("targetmode=\"external\"")
                || compact.contains("targetmode='external'")
                || compact.contains("macroenabled")
                || compact.contains("vbaproject")
                || compact.contains("activex")
                || compact.contains("oleobject");
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P';
    }

    private boolean isPlainText(byte[] header) {
        if (header.length >= 2 && ((header[0] == (byte) 0xFF && header[1] == (byte) 0xFE)
                || (header[0] == (byte) 0xFE && header[1] == (byte) 0xFF))) {
            return true;
        }
        for (byte value : header) {
            if (value == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWith(byte[] header, byte[] expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMagic(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private Path storedFilePath(FileInfo fileInfo) {
        Path uploadDir = resolveUploadDir();
        Path file = uploadDir.resolve(storageName(fileInfo)).normalize();
        if (!file.startsWith(uploadDir)) {
            throw ApiException.badRequest("invalid stored file path");
        }
        return file;
    }

    private String storageName(FileInfo fileInfo) {
        String fileUrl = fileInfo == null ? null : fileInfo.getFileUrl();
        if (fileUrl == null || !fileUrl.startsWith("/files/")) {
            throw ApiException.badRequest("invalid stored file path");
        }
        String name = fileUrl.substring("/files/".length());
        if (name.isBlank()
                || name.contains("/")
                || name.contains("\\")
                || name.contains(":")
                || name.contains("\0")
                || name.startsWith("..")) {
            throw ApiException.badRequest("invalid stored file path");
        }
        return name;
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException("delete file failed", e);
        }
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    private record ImageSize(int width, int height) {
    }
}
