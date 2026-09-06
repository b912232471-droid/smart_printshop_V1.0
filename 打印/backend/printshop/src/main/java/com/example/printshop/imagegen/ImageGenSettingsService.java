package com.example.printshop.imagegen;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.ImageGenSettings;
import com.example.printshop.mapper.ImageGenSettingAuditMapper;
import com.example.printshop.mapper.ImageGenSettingsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * AI 图片生成运营配置：单行表 + 60s 进程内缓存 + 变更审计（镜像 chat_settings 模式）
 */
@Service
public class ImageGenSettingsService {
    private static final Logger log = LoggerFactory.getLogger(ImageGenSettingsService.class);
    private static final long CACHE_MS = 60_000;

    private final ImageGenSettingsMapper settingsMapper;
    private final ImageGenSettingAuditMapper auditMapper;
    private final ObjectMapper objectMapper;
    private volatile ImageGenSettings cached;
    private volatile long cachedAt;

    public ImageGenSettingsService(ImageGenSettingsMapper settingsMapper,
                                   ImageGenSettingAuditMapper auditMapper,
                                   ObjectMapper objectMapper) {
        this.settingsMapper = settingsMapper;
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
    }

    public ImageGenSettings get() {
        ImageGenSettings snapshot = cached;
        long age = System.currentTimeMillis() - cachedAt;
        if (snapshot != null && age < CACHE_MS) {
            return snapshot;
        }
        synchronized (this) {
            age = System.currentTimeMillis() - cachedAt;
            if (cached != null && age < CACHE_MS) {
                return cached;
            }
            ImageGenSettings loaded = settingsMapper.selectOne();
            if (loaded == null) {
                loaded = defaults();
            }
            cached = loaded;
            cachedAt = System.currentTimeMillis();
            return loaded;
        }
    }

    public void update(ImageGenSettings incoming, Long adminId) {
        validate(incoming);
        ImageGenSettings current = settingsMapper.selectOne();
        if (current == null) {
            current = defaults();
        }
        incoming.setId(1);
        incoming.setUpdatedBy(adminId);
        int rows = settingsMapper.update(incoming);
        if (rows <= 0) {
            throw ApiException.serviceUnavailable("配置保存失败，请稍后重试");
        }
        writeAudit(current, incoming, adminId);
        cached = null;
        cachedAt = 0;
    }

    private void validate(ImageGenSettings settings) {
        if (settings == null) {
            throw ApiException.badRequest("配置不能为空");
        }
        if (settings.getEnabled() == null) settings.setEnabled(1);
        if (settings.getDefaultModel() == null || settings.getDefaultModel().isBlank()) {
            throw ApiException.badRequest("默认模型不能为空");
        }
        if (settings.getDailyQuotaPerUser() == null || settings.getDailyQuotaPerUser() < 0 || settings.getDailyQuotaPerUser() > 100) {
            throw ApiException.badRequest("每用户每日生成张数须在 0-100 之间");
        }
        if (settings.getMaxPromptLength() == null || settings.getMaxPromptLength() < 50 || settings.getMaxPromptLength() > 2000) {
            throw ApiException.badRequest("提示词长度上限须在 50-2000 之间");
        }
        if (settings.getPolishEnabled() == null) settings.setPolishEnabled(1);
        if (settings.getModerationEnabled() == null) settings.setModerationEnabled(1);
        if (settings.getTemplatesEnabled() == null) settings.setTemplatesEnabled(1);
        if (settings.getWatermarkEnabled() == null) settings.setWatermarkEnabled(0);
        if (settings.getWatermarkText() != null && settings.getWatermarkText().length() > 64) {
            throw ApiException.badRequest("水印文案不能超过 64 字");
        }
        if (settings.getDailyCostCap() == null
                || settings.getDailyCostCap().compareTo(BigDecimal.ZERO) < 0
                || settings.getDailyCostCap().compareTo(new BigDecimal("100000")) > 0) {
            throw ApiException.badRequest("单日成本上限须在 0-100000 元之间（0 表示不限制）");
        }
    }

    private void writeAudit(ImageGenSettings before, ImageGenSettings after, Long adminId) {
        try {
            String beforeJson = objectMapper.writeValueAsString(before);
            String afterJson = objectMapper.writeValueAsString(after);
            auditMapper.insert(adminId, beforeJson, afterJson);
        } catch (Exception exception) {
            log.warn("imagegen settings audit write failed adminId={}", adminId, exception);
        }
    }

    private ImageGenSettings defaults() {
        ImageGenSettings settings = new ImageGenSettings();
        settings.setId(1);
        settings.setEnabled(1);
        settings.setDefaultModel("seedream-image-v5.0-lite");
        settings.setDailyQuotaPerUser(5);
        settings.setMaxPromptLength(500);
        settings.setPolishEnabled(1);
        settings.setModerationEnabled(1);
        settings.setTemplatesEnabled(1);
        settings.setWatermarkEnabled(0);
        settings.setDailyCostCap(new BigDecimal("50.00"));
        return settings;
    }
}
