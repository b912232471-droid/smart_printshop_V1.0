package com.example.printshop.ocr;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.OcrSettings;
import com.example.printshop.mapper.OcrSettingAuditMapper;
import com.example.printshop.mapper.OcrSettingsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * OCR 图片转文档运营配置：单行表 + 60s 进程内缓存 + 变更审计（镜像 imagegen/chat 模式）
 */
@Service
public class OcrSettingsService {
    private static final Logger log = LoggerFactory.getLogger(OcrSettingsService.class);
    private static final long CACHE_MS = 60_000;

    private final OcrSettingsMapper settingsMapper;
    private final OcrSettingAuditMapper auditMapper;
    private final ObjectMapper objectMapper;
    private volatile OcrSettings cached;
    private volatile long cachedAt;

    public OcrSettingsService(OcrSettingsMapper settingsMapper,
                              OcrSettingAuditMapper auditMapper,
                              ObjectMapper objectMapper) {
        this.settingsMapper = settingsMapper;
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
    }

    public OcrSettings get() {
        OcrSettings snapshot = cached;
        long age = System.currentTimeMillis() - cachedAt;
        if (snapshot != null && age < CACHE_MS) {
            return snapshot;
        }
        synchronized (this) {
            age = System.currentTimeMillis() - cachedAt;
            if (cached != null && age < CACHE_MS) {
                return cached;
            }
            OcrSettings loaded = settingsMapper.selectOne();
            if (loaded == null) {
                loaded = defaults();
            }
            cached = loaded;
            cachedAt = System.currentTimeMillis();
            return loaded;
        }
    }

    public void update(OcrSettings incoming, Long adminId) {
        validate(incoming);
        OcrSettings current = settingsMapper.selectOne();
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

    private void validate(OcrSettings settings) {
        if (settings == null) {
            throw ApiException.badRequest("配置不能为空");
        }
        if (settings.getEnabled() == null) settings.setEnabled(1);
        if (settings.getDailyQuotaPerUser() == null
                || settings.getDailyQuotaPerUser() < 0
                || settings.getDailyQuotaPerUser() > 200) {
            throw ApiException.badRequest("每用户每日识别次数须在 0-200 之间");
        }
    }

    private OcrSettings defaults() {
        OcrSettings settings = new OcrSettings();
        settings.setId(1);
        settings.setEnabled(1);
        settings.setDailyQuotaPerUser(20);
        return settings;
    }

    private void writeAudit(OcrSettings before, OcrSettings after, Long adminId) {
        try {
            auditMapper.insert(adminId,
                    objectMapper.writeValueAsString(before),
                    objectMapper.writeValueAsString(after));
        } catch (Exception exception) {
            log.warn("ocr settings audit write failed adminId={}", adminId, exception);
        }
    }
}
