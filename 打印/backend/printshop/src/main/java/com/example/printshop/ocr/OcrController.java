package com.example.printshop.ocr;

import com.example.printshop.common.ApiResponse;
import com.example.printshop.common.ApiException;
import com.example.printshop.entity.OcrRecord;
import com.example.printshop.entity.OcrSettings;
import com.example.printshop.mapper.OcrRecordMapper;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
    private final OcrSettingsService settingsService;
    private final OcrQuotaService quotaService;
    private final com.example.printshop.account.AccountQuotaService accountQuotaService;
    private final OcrRecordMapper recordMapper;
    private final int maxFileSizeMb;

    public OcrController(BaiduOcrService ocrService,
                         OcrSettingsService settingsService,
                         OcrQuotaService quotaService,
                         com.example.printshop.account.AccountQuotaService accountQuotaService,
                         OcrRecordMapper recordMapper,
                         @Value("${baidu.ocr.max-file-size-mb:4}") int maxFileSizeMb) {
        this.ocrService = ocrService;
        this.settingsService = settingsService;
        this.quotaService = quotaService;
        this.accountQuotaService = accountQuotaService;
        this.recordMapper = recordMapper;
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
        OcrSettings settings = settingsService.get();
        if (settings.getEnabled() == null || settings.getEnabled() != 1) {
            throw ApiException.serviceUnavailable("OCR 图片转文档功能未开启");
        }
        int effectiveQuota = accountQuotaService.resolveDailyQuota(
                principal.getId(),
                com.example.printshop.account.AccountQuotaService.FEATURE_OCR,
                settings.getDailyQuotaPerUser());
        quotaService.tryConsume(principal.getId(), effectiveQuota);

        String displayName = safeName(file);
        long started = System.currentTimeMillis();
        String text;
        try {
            text = ocrService.recognize(file);
        } catch (RuntimeException exception) {
            writeRecord(principal.getId(), displayName, file.getSize(), 0,
                    (int) (System.currentTimeMillis() - started), 2, failureReason(exception, "文字识别失败"));
            throw exception;
        }
        int durationMs = (int) (System.currentTimeMillis() - started);

        String token = UUID.randomUUID().toString();
        Path dir = ocrService.outputDir(principal.getId());
        Map<String, Object> files = new LinkedHashMap<>();
        List<String> failedFormats = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>();
        if (!"pdf".equals(format)) {
            try {
                ocrService.createDocx(text, dir.resolve(token + ".docx"));
                files.put("docx", downloadUrl(principal.getId(), token, "docx"));
                files.put("docxName", displayName + ".docx");
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
                files.put("pdfName", displayName + ".pdf");
            } catch (Exception e) {
                log.error("OCR pdf generation failed, token={}", token, e);
                failedFormats.add("pdf");
                failureMessages.add(failureReason(e, "PDF 文档生成失败"));
            }
        }
        if (files.isEmpty()) {
            String reason = String.join("；", failureMessages);
            writeRecord(principal.getId(), displayName, file.getSize(), text.length(), durationMs, 2,
                    reason.isBlank() ? "OCR 文档生成失败" : reason);
            throw ApiException.serviceUnavailable(reason.isBlank() ? "OCR 文档生成失败" : reason);
        }
        writeRecord(principal.getId(), displayName, file.getSize(), text.length(), durationMs, 1, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        data.put("files", files);
        data.put("ownerId", principal.getId());
        data.put("token", token);
        data.put("fileName", displayName);
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
        OcrSettings settings = settingsService.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "Baidu OCR");
        data.put("configured", ocrService.isConfigured());
        data.put("enabled", settings.getEnabled() != null && settings.getEnabled() == 1);
        data.put("formats", formats);
        data.put("pdfFontAvailable", pdfFontAvailable);
        data.put("maxFileSizeMb", maxFileSizeMb);
        data.put("endpoint", "accurate_basic");
        return ApiResponse.ok(data);
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        AuthContext.requirePermission("print:ocr:manage");
        OcrSettings settings = settingsService.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", settings.getId());
        data.put("enabled", settings.getEnabled());
        data.put("dailyQuotaPerUser", settings.getDailyQuotaPerUser());
        data.put("updatedBy", settings.getUpdatedBy());
        data.put("updatedAt", settings.getUpdatedAt());
        data.put("provider", "Baidu OCR");
        data.put("configured", ocrService.isConfigured());
        data.put("maxFileSizeMb", maxFileSizeMb);
        return ApiResponse.ok(data);
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(@RequestBody OcrSettings settings) {
        AuthPrincipal principal = AuthContext.requirePermission("print:ocr:manage");
        settingsService.update(settings, principal.getId() == null ? null : principal.getId().longValue());
        return config();
    }

    @GetMapping("/records")
    public ApiResponse<Map<String, Object>> records(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) Long accountId,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) String start,
                                                    @RequestParam(required = false) String end) {
        AuthContext.requirePermission("print:ocr:query");
        int[] range = pagingRange(page, size);
        int total = recordMapper.countAll(accountId, status, start, end);
        List<OcrRecord> items = total == 0
                ? List.of()
                : recordMapper.selectAll(accountId, status, start, end, range[0], range[1]);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", range[1]);
        data.put("items", items);
        return ApiResponse.ok(data);
    }

    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage(@RequestParam(required = false) String start,
                                                  @RequestParam(required = false) String end) {
        AuthContext.requirePermission("print:ocr:query");
        String effectiveEnd = end == null || end.isBlank() ? LocalDate.now().toString() : end;
        String effectiveStart = start == null || start.isBlank()
                ? LocalDate.now().minusDays(29).toString() : start;
        Map<String, Object> usage = recordMapper.aggregateUsage(effectiveStart, effectiveEnd);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("start", effectiveStart);
        data.put("end", effectiveEnd);
        data.put("totalCount", usage == null ? 0 : usage.get("totalCount"));
        data.put("successCount", usage == null ? 0 : usage.get("successCount"));
        data.put("failedCount", usage == null ? 0 : usage.get("failedCount"));
        data.put("totalCharCount", usage == null ? 0 : usage.get("totalCharCount"));
        data.put("avgDurationMs", usage == null ? 0 : usage.get("avgDurationMs"));
        return ApiResponse.ok(data);
    }

    private void writeRecord(Integer accountId, String fileName, long fileSize, int charCount,
                             int durationMs, int status, String failureReason) {
        try {
            OcrRecord record = new OcrRecord();
            record.setAccountId(accountId == null ? null : accountId.longValue());
            record.setFileName(fileName);
            record.setFileSize(fileSize);
            record.setCharCount(charCount);
            record.setDurationMs(durationMs);
            record.setStatus(status);
            record.setFailureReason(failureReason);
            recordMapper.insert(record);
        } catch (Exception exception) {
            log.warn("OCR record write failed accountId={}", accountId, exception);
        }
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

    private int[] pagingRange(int page, int size) {
        int effectivePage = Math.max(page, 1);
        int effectiveSize = Math.min(Math.max(size, 1), 50);
        return new int[]{(effectivePage - 1) * effectiveSize, effectiveSize};
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


