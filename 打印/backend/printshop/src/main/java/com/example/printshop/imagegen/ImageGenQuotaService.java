package com.example.printshop.imagegen;

import com.example.printshop.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * AI 图片生成每日配额（Redis 日计数，按自然日重置）
 */
@Service
public class ImageGenQuotaService {
    private static final String KEY_PREFIX = "printshop:imagegen:quota:";

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public ImageGenQuotaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 消耗一次配额，返回消耗后剩余张数；超出上限抛 429。
     * Redis 不可用时按 fail-closed 拒绝（与登录验证码/邮件验证码语义一致）。
     */
    public int tryConsume(Integer accountId, int dailyQuota) {
        if (accountId == null) {
            throw ApiException.forbidden("authentication required");
        }
        if (dailyQuota <= 0) {
            throw ApiException.tooManyRequests("今日生成次数已用完，请明天再试");
        }
        String key = KEY_PREFIX + accountId + ":" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long used;
        try {
            used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(48));
            }
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("生成配额服务暂不可用，请稍后重试");
        }
        if (used != null && used > dailyQuota) {
            throw ApiException.tooManyRequests("今日生成次数已用完（每日 " + dailyQuota + " 张），请明天再试");
        }
        return (int) Math.max(0, dailyQuota - (used == null ? 0 : used));
    }
}
