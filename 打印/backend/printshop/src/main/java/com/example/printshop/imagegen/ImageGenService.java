package com.example.printshop.imagegen;

import com.example.printshop.account.AccountQuotaService;
import com.example.printshop.common.ApiException;
import com.example.printshop.entity.ImageGenRecord;
import com.example.printshop.entity.ImageGenSettings;
import com.example.printshop.mapper.ImageGenRecordMapper;
import com.example.printshop.security.ImageContentModerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AI 图片生成编排（方案 5.1.2）：
 * 配置开关 -> 提示词校验 -> 配额 -> 成本熔断 -> 模型白名单 -> 润色(可选)
 * -> photo-service 模型调用 -> 内容审核 -> 落盘 -> 记录落库
 */
@Service
public class ImageGenService {
    private static final Logger log = LoggerFactory.getLogger(ImageGenService.class);
    private static final Logger alertLog = LoggerFactory.getLogger("SECURITY_ALERT");
    private static final long MODEL_CACHE_MS = 5 * 60_000;
    private static final long COST_CACHE_MS = 60_000;
    private static final long MAX_IMAGE_BYTES = 30 * 1024 * 1024;

    private final ImageGenRecordMapper recordMapper;
    private final ImageGenSettingsService settingsService;
    private final ImageGenQuotaService quotaService;
    private final AccountQuotaService accountQuotaService;
    private final ImageGenStorageService storageService;
    private final PromptPolishService polishService;
    private final ImageContentModerationService moderationService;
    private final RestTemplate imageGenRestTemplate;
    private final ObjectMapper objectMapper;
    private final String photoBaseUrl;
    private final List<String> blocklist;

    private volatile Map<String, Object> modelCatalogCache;
    private volatile long modelCatalogAt;
    private volatile BigDecimal dailyCostSnapshot = BigDecimal.ZERO;
    private volatile long dailyCostAt;

    @Autowired
    public ImageGenService(ImageGenRecordMapper recordMapper,
                           ImageGenSettingsService settingsService,
                           ImageGenQuotaService quotaService,
                           AccountQuotaService accountQuotaService,
                           ImageGenStorageService storageService,
                           PromptPolishService polishService,
                           ImageContentModerationService moderationService,
                           @Qualifier("imageGenRestTemplate") RestTemplate imageGenRestTemplate,
                           ObjectMapper objectMapper,
                           @Value("${printshop.imagegen.photo-base-url:http://photo-service:8091}") String photoBaseUrl,
                           @Value("${printshop.imagegen.prompt-blocklist:}") String promptBlocklist) {
        this.recordMapper = recordMapper;
        this.settingsService = settingsService;
        this.quotaService = quotaService;
        this.accountQuotaService = accountQuotaService;
        this.storageService = storageService;
        this.polishService = polishService;
        this.moderationService = moderationService;
        this.imageGenRestTemplate = imageGenRestTemplate;
        this.objectMapper = objectMapper;
        this.photoBaseUrl = photoBaseUrl == null ? "http://photo-service:8091" : photoBaseUrl.trim();
        List<String> words = new ArrayList<>();
        if (promptBlocklist != null && !promptBlocklist.isBlank()) {
            for (String word : promptBlocklist.split("[,，]")) {
                if (!word.isBlank()) {
                    words.add(word.trim());
                }
            }
        }
        this.blocklist = words;
    }

    /** 模型目录（photo-service 代理，5 分钟缓存；photo 不可用但缓存存在时返回旧值） */
    public Map<String, Object> modelCatalog() {
        long age = System.currentTimeMillis() - modelCatalogAt;
        Map<String, Object> snapshot = modelCatalogCache;
        if (snapshot != null && age < MODEL_CACHE_MS) {
            return snapshot;
        }
        synchronized (this) {
            age = System.currentTimeMillis() - modelCatalogAt;
            if (modelCatalogCache != null && age < MODEL_CACHE_MS) {
                return modelCatalogCache;
            }
            try {
                ResponseEntity<String> response = imageGenRestTemplate.getForEntity(
                        photoBaseUrl + "/api/photo/image-models", String.class);
                JsonNode data = objectMapper.readTree(response.getBody()).path("data");
                if (data.isObject() && data.path("models").isArray()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> catalog = objectMapper.convertValue(data, Map.class);
                    modelCatalogCache = catalog;
                    modelCatalogAt = System.currentTimeMillis();
                    return catalog;
                }
            } catch (Exception exception) {
                log.warn("imagegen model catalog fetch failed: {}", exception.getMessage());
            }
            return modelCatalogCache;
        }
    }

    public Map<String, Object> generate(Integer accountId, String authorization, String model, String prompt,
                                        String size, String templateKey, boolean polish) {
        ImageGenSettings settings = settingsService.get();
        if (settings.getEnabled() == null || settings.getEnabled() != 1) {
            throw ApiException.serviceUnavailable("AI 图片生成功能未开启");
        }
        String cleanedPrompt = sanitizePrompt(prompt, settings.getMaxPromptLength());

        int effectiveQuota = accountQuotaService.resolveDailyQuota(
                accountId, AccountQuotaService.FEATURE_IMAGEGEN, settings.getDailyQuotaPerUser());
        int quotaRemaining = quotaService.tryConsume(accountId, effectiveQuota);
        checkDailyCostCap(settings);

        Map<String, Object> catalog = modelCatalog();
        BigDecimal costAmount = requireModelInCatalog(catalog, model);

        String finalPrompt = cleanedPrompt;
        boolean polishedFlag = false;
        if (polish && settings.getPolishEnabled() != null && settings.getPolishEnabled() == 1) {
            try {
                String polishedPrompt = polishService.polish(cleanedPrompt);
                String safePolished = trySanitize(polishedPrompt, settings.getMaxPromptLength());
                if (safePolished != null && !safePolished.isBlank()) {
                    finalPrompt = safePolished;
                    polishedFlag = true;
                }
            } catch (Exception exception) {
                log.warn("imagegen polish degraded accountId={} reason={}", accountId, exception.getMessage());
            }
        }

        String watermarkText = settings.getWatermarkEnabled() != null && settings.getWatermarkEnabled() == 1
                && settings.getWatermarkText() != null && !settings.getWatermarkText().isBlank()
                ? settings.getWatermarkText().trim() : null;

        PhotoResult photoResult = callPhotoService(authorization, model, finalPrompt, size, watermarkText);
        byte[] imageBytes = photoResult.bytes();

        int moderationStatus = 1;
        if (settings.getModerationEnabled() == null || settings.getModerationEnabled() == 1) {
            try {
                moderationService.moderate(pngMultipartFile(imageBytes));
            } catch (ApiException exception) {
                if (exception.getStatus() == HttpStatus.BAD_REQUEST.value()) {
                    recordRejected(accountId, model, finalPrompt, templateKey, size, polishedFlag);
                    throw ApiException.badRequest("生成内容未通过合规检测，请调整描述后重试");
                }
                throw exception;
            }
        }

        String token = UUID.randomUUID().toString();
        String fileUrl = storageService.save(accountId, token, imageBytes);
        int[] dimensions = parseDimensions(size);

        ImageGenRecord record = new ImageGenRecord();
        record.setAccountId(accountId);
        record.setToken(token);
        record.setModelId(model);
        record.setPrompt(finalPrompt);
        record.setPolished(polishedFlag ? 1 : 0);
        record.setTemplateKey(templateKey);
        record.setFileUrl(fileUrl);
        record.setWidth(dimensions[0]);
        record.setHeight(dimensions[1]);
        record.setSize(size);
        record.setCostAmount(costAmount);
        record.setModerationStatus(moderationStatus);
        record.setStatus(1);
        record.setOrderId(null);
        record.setUsageTokens(photoResult.usageTokens());
        record.setDurationMs(photoResult.durationMs());
        recordMapper.insert(record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        result.put("token", token);
        result.put("model", model);
        result.put("prompt", finalPrompt);
        result.put("polished", polishedFlag);
        result.put("templateKey", templateKey);
        result.put("width", dimensions[0]);
        result.put("height", dimensions[1]);
        result.put("size", size);
        result.put("downloadUrl", "/api/print/imagegen/download/" + accountId + "/" + token);
        result.put("previewUrl", "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes));
        result.put("costAmount", costAmount);
        result.put("quotaRemaining", quotaRemaining);
        return result;
    }

    private record PhotoResult(byte[] bytes, Integer usageTokens, Integer durationMs) {
    }

    private PhotoResult callPhotoService(String authorization, String model, String prompt, String size, String watermarkText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorization != null && !authorization.isBlank()) {
            headers.set("Authorization", authorization);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("size", size);
        if (watermarkText != null) {
            payload.put("watermark_text", watermarkText);
        }
        try {
            ResponseEntity<String> response = imageGenRestTemplate.postForEntity(
                    photoBaseUrl + "/api/photo/image-generate", new HttpEntity<>(payload, headers), String.class);
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            String b64 = data.path("image").asText(null);
            if (b64 == null || b64.isBlank()) {
                throw ApiException.serviceUnavailable("图片生成服务未返回图片数据");
            }
            Integer usageTokens = data.path("usage_tokens").isInt() ? data.path("usage_tokens").asInt() : null;
            Integer durationMs = data.path("duration_ms").isInt() ? data.path("duration_ms").asInt() : null;
            byte[] bytes = Base64.getDecoder().decode(b64);
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw ApiException.serviceUnavailable("生成的图片数据异常");
            }
            return new PhotoResult(bytes, usageTokens, durationMs);
        } catch (ApiException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            String body = exception.getResponseBodyAsString();
            log.warn("imagegen photo-service http error status={} body={}",
                    exception.getStatusCode(), abbreviate(body));
            throw ApiException.serviceUnavailable(mapUpstreamError(body));
        } catch (ResourceAccessException exception) {
            log.warn("imagegen photo-service connect error: {}", exception.getMessage());
            throw ApiException.serviceUnavailable("图片生成服务连接超时，请稍后重试");
        } catch (Exception exception) {
            log.warn("imagegen photo-service decode error: {}", exception.getMessage());
            throw ApiException.serviceUnavailable("图片生成失败，请稍后重试");
        }
    }

    /** 上游计费/额度类错误映射为可操作提示，其余保持通用文案（完整原始错误只在日志中） */
    private String mapUpstreamError(String body) {
        if (body != null && !body.isBlank()) {
            String lower = body.toLowerCase(Locale.ROOT);
            if (lower.contains("quota") || lower.contains("billing") || lower.contains("postpaid")
                    || lower.contains("balance") || lower.contains("余额") || lower.contains("401007")) {
                return "图片生成服务额度不足或计费未开通，请联系管理员处理";
            }
            if (lower.contains("timeout") || lower.contains("timed out")) {
                return "图片生成超时，请稍后重试";
            }
        }
        return "图片生成服务暂时不可用，请稍后重试";
    }

    private void checkDailyCostCap(ImageGenSettings settings) {
        BigDecimal cap = settings.getDailyCostCap();
        if (cap == null || cap.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - dailyCostAt > COST_CACHE_MS) {
            synchronized (this) {
                if (now - dailyCostAt > COST_CACHE_MS) {
                    dailyCostSnapshot = recordMapper.sumCostSinceToday();
                    dailyCostAt = System.currentTimeMillis();
                }
            }
        }
        if (dailyCostSnapshot.compareTo(cap) >= 0) {
            alertLog.warn("event=imagegen_daily_cost_cap_exceeded severity=high cap={} used={}", cap, dailyCostSnapshot);
            throw ApiException.serviceUnavailable("今日平台生成额度已用完，请明天再试");
        }
    }

    private BigDecimal requireModelInCatalog(Map<String, Object> catalog, String model) {
        if (catalog == null) {
            // 模型目录拉取失败时跳过本地白名单，photo-service 端仍会校验
            return null;
        }
        Object models = catalog.get("models");
        if (models instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> entry && model.equals(String.valueOf(entry.get("id")))) {
                    Object price = entry.get("pricePerImage");
                    if (price instanceof Number number) {
                        return BigDecimal.valueOf(number.doubleValue());
                    }
                    return null;
                }
            }
        }
        throw ApiException.badRequest("不支持的模型");
    }

    private String sanitizePrompt(String raw, Integer maxLength) {
        String prompt = raw == null ? "" : raw.trim();
        if (prompt.isEmpty()) {
            throw ApiException.badRequest("请输入图片描述");
        }
        int max = maxLength == null || maxLength <= 0 ? 500 : maxLength;
        if (prompt.length() > max) {
            throw ApiException.badRequest("图片描述过长，请控制在 " + max + " 字以内");
        }
        if (blocklistHit(prompt)) {
            throw ApiException.badRequest("图片描述包含不允许的内容，请调整后重试");
        }
        return prompt;
    }

    private String trySanitize(String raw, Integer maxLength) {
        try {
            return sanitizePrompt(raw, maxLength);
        } catch (ApiException exception) {
            return null;
        }
    }

    private boolean blocklistHit(String prompt) {
        if (blocklist.isEmpty()) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        for (String word : blocklist) {
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void recordRejected(Integer accountId, String model, String prompt, String templateKey,
                                String size, boolean polished) {
        try {
            ImageGenRecord record = new ImageGenRecord();
            record.setAccountId(accountId);
            record.setToken(UUID.randomUUID().toString());
            record.setModelId(model);
            record.setPrompt(prompt);
            record.setPolished(polished ? 1 : 0);
            record.setTemplateKey(templateKey);
            record.setFileUrl("");
            int[] dimensions = parseDimensions(size);
            record.setWidth(dimensions[0]);
            record.setHeight(dimensions[1]);
            record.setSize(size);
            record.setModerationStatus(2);
            record.setStatus(1);
            recordMapper.insert(record);
        } catch (Exception exception) {
            log.warn("imagegen rejected-record write failed accountId={}", accountId, exception);
        }
    }

    private int[] parseDimensions(String size) {
        try {
            String[] parts = size.toLowerCase(Locale.ROOT).split("x");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (Exception exception) {
            return new int[]{0, 0};
        }
    }

    private MultipartFile pngMultipartFile(byte[] bytes) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "image";
            }

            @Override
            public String getOriginalFilename() {
                return "generated.png";
            }

            @Override
            public String getContentType() {
                return MediaType.IMAGE_PNG_VALUE;
            }

            @Override
            public boolean isEmpty() {
                return bytes == null || bytes.length == 0;
            }

            @Override
            public long getSize() {
                return bytes == null ? 0 : bytes.length;
            }

            @Override
            public byte[] getBytes() {
                return bytes;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void transferTo(File dest) throws IOException {
                Files.write(dest.toPath(), bytes);
            }
        };
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
