package com.example.printshop.ocr;

import com.example.printshop.common.ApiResponse;
import com.example.printshop.common.ApiException;
import com.example.printshop.security.AuthContext;
import com.example.printshop.security.AuthPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {
    private static final Logger log = LoggerFactory.getLogger(OcrController.class);

    private final BaiduOcrService ocrService;
    private final int maxFileSizeMb;

    public OcrController(BaiduOcrService ocrService,
                         @Value("${baidu.ocr.max-file-size-mb:4}") int maxFileSizeMb) {
        this.ocrService = ocrService;
        this.maxFileSizeMb = maxFileSizeMb;
    }

    @PostMapping("/convert")
    public ApiResponse<Map<String, Object>> convert(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "format", defaultValue = "both") String format) {
        AuthPrincipal principal = AuthContext.get();
        if (!principal.isUser() && !principal.isAdmin()) {
            throw ApiException.forbidden("user permission required");
        }
        if (!("docx".equals(format) || "pdf".equals(format) || "both".equals(format))) {
            throw ApiException.badRequest("format must be docx, pdf or both");
        }
        String text = ocrService.recognize(file);
        String token = UUID.randomUUID().toString();
        Path dir = ocrService.outputDir(principal.getId());
        Map<String, Object> files = new LinkedHashMap<>();
        List<String> failedFormats = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>();
        if (!"pdf".equals(format)) {
            try {
                ocrService.createDocx(text, dir.resolve(token + ".docx"));
                files.put("docx", downloadUrl(principal.getId(), token, "docx"));
                files.put("docxName", safeName(file) + ".docx");
            } catch (Exception e) {
                log.error("OCR docx generation failed, token={}", token, e);
                failedFormats.add("docx");
                failureMessages.add(failureReason(e, "Word 文档生成失败"));
            }
        }
        if (!"docx".equals(format)) {
            try {
                ocrService.createPdf(text, dir.resolve(token + ".pdf"));
                files.put("pdf", downloadUrl(principal.getId(), token, "pdf"));
                files.put("pdfName", safeName(file) + ".pdf");
            } catch (Exception e) {
                log.error("OCR pdf generation failed, token={}", token, e);
                failedFormats.add("pdf");
                failureMessages.add(failureReason(e, "PDF 文档生成失败"));
            }
        }
        if (files.isEmpty()) {
            String reason = String.join("；", failureMessages);
            throw ApiException.serviceUnavailable(reason.isBlank() ? "OCR 文档生成失败" : reason);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        data.put("files", files);
        data.put("ownerId", principal.getId());
        data.put("token", token);
        data.put("fileName", safeName(file));
        if (!failedFormats.isEmpty()) {
            data.put("failedFormats", failedFormats);
            data.put("failureMessage", String.join("；", failureMessages));
        }
        return ApiResponse.ok(data);
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        AuthContext.requireAdmin();
        boolean pdfFontAvailable = ocrService.isPdfFontAvailable();
        List<String> formats = new ArrayList<>();
        formats.add("docx");
        if (pdfFontAvailable) {
            formats.add("pdf");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "Baidu OCR");
        data.put("configured", ocrService.isConfigured());
        data.put("formats", formats);
        data.put("pdfFontAvailable", pdfFontAvailable);
        data.put("maxFileSizeMb", maxFileSizeMb);
        data.put("endpoint", "accurate_basic");
        return ApiResponse.ok(data);
    }

    @GetMapping("/download/{ownerId}/{token}/{format}")
    public ResponseEntity<Resource> download(@PathVariable Integer ownerId,
                                             @PathVariable String token,
                                             @PathVariable String format) {
        AuthPrincipal principal = AuthContext.get();
        if (!principal.isAdmin() && !ownerId.equals(principal.getId())) {
            throw ApiException.forbidden("cannot access another user's OCR file");
        }
        Path file = ocrService.resolveDownload(ownerId, token, format);
        String filename = "ocr-result." + format;
        Resource resource = new FileSystemResource(file);
        MediaType mediaType = "pdf".equals(format)
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentLength(file.toFile().length())
                .body(resource);
    }

    private String downloadUrl(Integer ownerId, String token, String format) {
        return "/api/print/ocr/download/" + ownerId + "/" + token + "/" + format;
    }

    private String failureReason(Exception e, String fallback) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    private String safeName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "ocr-result" : file.getOriginalFilename();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replaceAll("[^\\p{L}\\p{N}_-]", "_");
    }
}


