package com.example.printshop.account;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.AccountQuota;
import com.example.printshop.mapper.AccountQuotaMapper;
import org.springframework.stereotype.Service;

/**
 * 账户级功能额度覆盖：NULL 跟随全局默认，0 表示对该账户禁用对应功能。
 */
@Service
public class AccountQuotaService {
    public static final String FEATURE_IMAGEGEN = "imagegen";
    public static final String FEATURE_OCR = "ocr";
    private static final int MAX_DAILY_LIMIT = 9999;

    private final AccountQuotaMapper accountQuotaMapper;

    public AccountQuotaService(AccountQuotaMapper accountQuotaMapper) {
        this.accountQuotaMapper = accountQuotaMapper;
    }

    /**
     * 计算账户在指定功能上的生效每日额度。
     */
    public int resolveDailyQuota(Integer accountId, String feature, int globalDefault) {
        if (accountId == null) {
            return globalDefault;
        }
        AccountQuota quota = accountQuotaMapper.selectByAccountId(accountId);
        if (quota == null) {
            return globalDefault;
        }
        Integer override = FEATURE_OCR.equals(feature) ? quota.getOcrDailyLimit() : quota.getImagegenDailyLimit();
        return override == null ? globalDefault : override;
    }

    public AccountQuota getQuota(Integer accountId) {
        return accountId == null ? null : accountQuotaMapper.selectByAccountId(accountId);
    }

    public void setQuota(Integer accountId, Integer imagegenDailyLimit, Integer ocrDailyLimit, Long updatedBy) {
        validateLimit(imagegenDailyLimit, "AI 图片生成");
        validateLimit(ocrDailyLimit, "OCR 识别");
        AccountQuota quota = new AccountQuota();
        quota.setAccountId(accountId);
        quota.setImagegenDailyLimit(imagegenDailyLimit);
        quota.setOcrDailyLimit(ocrDailyLimit);
        quota.setUpdatedBy(updatedBy);
        accountQuotaMapper.upsert(quota);
    }

    private void validateLimit(Integer limit, String featureName) {
        if (limit == null) {
            return;
        }
        if (limit < 0 || limit > MAX_DAILY_LIMIT) {
            throw ApiException.badRequest(featureName + "每日额度须在 0 到 " + MAX_DAILY_LIMIT + " 之间，留空表示跟随全局默认");
        }
    }
}
