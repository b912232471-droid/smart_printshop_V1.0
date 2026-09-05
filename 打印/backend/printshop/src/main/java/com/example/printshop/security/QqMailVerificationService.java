package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class QqMailVerificationService {
    private static final DefaultRedisScript<Long> VERIFY_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    public static final String REGISTER = "register";
    public static final String RESET_PASSWORD = "reset_password";
    public static final String BIND_EMAIL = "bind_email";
    public static final String ADMIN_REGISTER = "admin_register";
    private static final Set<String> PURPOSES = Set.of(REGISTER, RESET_PASSWORD, BIND_EMAIL, ADMIN_REGISTER);
    private static final String PREFIX = "printshop:email:";

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final FieldCryptoService cryptoService;
    private final SecureRandom random = new SecureRandom();
    private final boolean enabled;
    private final String from;
    private final long expireSeconds;
    private final long cooldownSeconds;
    private final int dailyLimit;

    public QqMailVerificationService(JavaMailSender mailSender,
                                     StringRedisTemplate redisTemplate,
                                     FieldCryptoService cryptoService,
                                     @Value("${printshop.mail.enabled:false}") boolean enabled,
                                     @Value("${printshop.mail.from:}") String from,
                                     @Value("${printshop.mail.verification.expire-seconds:300}") long expireSeconds,
                                     @Value("${printshop.mail.verification.cooldown-seconds:60}") long cooldownSeconds,
                                     @Value("${printshop.mail.verification.daily-limit:20}") int dailyLimit) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.cryptoService = cryptoService;
        this.enabled = enabled;
        this.from = from == null ? "" : from.trim();
        this.expireSeconds = Math.max(60, expireSeconds);
        this.cooldownSeconds = Math.max(30, cooldownSeconds);
        this.dailyLimit = Math.max(1, dailyLimit);
    }

    public long send(String rawEmail, String rawPurpose) {
        ensureConfigured();
        String email = QqEmailAddress.normalize(rawEmail);
        String purpose = normalizePurpose(rawPurpose);
        String emailHash = cryptoService.blindIndex(email);
        String cooldownKey = PREFIX + "cooldown:" + purpose + ":" + emailHash;
        String dailyKey = PREFIX + "daily:" + LocalDate.now() + ":" + emailHash;
        try {
            Boolean allowed = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));
            if (!Boolean.TRUE.equals(allowed)) {
                throw ApiException.tooManyRequests("验证码发送过于频繁，请稍后再试");
            }
            Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
            if (dailyCount != null && dailyCount == 1) {
                redisTemplate.expire(dailyKey, Duration.ofDays(2));
            }
            if (dailyCount != null && dailyCount > dailyLimit) {
                redisTemplate.delete(cooldownKey);
                throw ApiException.tooManyRequests("该邮箱今日验证码发送次数已达上限");
            }
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("验证码服务暂不可用，请稍后重试");
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        String codeKey = codeKey(purpose, emailHash);
        try {
            redisTemplate.opsForValue().set(codeKey, codeHash(email, purpose, code), Duration.ofSeconds(expireSeconds));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject(subject(purpose));
            message.setText("您的验证码是：" + code + "\n\n验证码 " + (expireSeconds / 60) + " 分钟内有效，请勿转发给他人。\n如非本人操作，请忽略此邮件。");
            mailSender.send(message);
            return expireSeconds;
        } catch (MailException exception) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(cooldownKey);
            throw ApiException.serviceUnavailable("QQ邮箱验证码发送失败，请检查邮件服务配置");
        }
    }

    public void verify(String rawEmail, String rawPurpose, String rawCode) {
        String email = QqEmailAddress.normalize(rawEmail);
        String purpose = normalizePurpose(rawPurpose);
        if (rawCode == null || !rawCode.trim().matches("^\\d{6}$")) {
            throw ApiException.badRequest("请输入6位邮箱验证码");
        }
        String emailHash = cryptoService.blindIndex(email);
        Long verified;
        try {
            verified = redisTemplate.execute(
                    VERIFY_AND_DELETE,
                    java.util.List.of(codeKey(purpose, emailHash)),
                    codeHash(email, purpose, rawCode.trim()));
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("验证码服务暂不可用，请稍后重试");
        }
        if (!Long.valueOf(1L).equals(verified)) {
            throw ApiException.badRequest("邮箱验证码错误或已过期");
        }
    }

    private void ensureConfigured() {
        if (!enabled || from.isBlank() || !QqEmailAddress.isQqEmail(from)) {
            throw ApiException.serviceUnavailable("QQ邮箱发送服务未配置");
        }
    }

    private String normalizePurpose(String value) {
        String purpose = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!PURPOSES.contains(purpose)) {
            throw ApiException.badRequest("非法验证码用途");
        }
        return purpose;
    }

    private String codeKey(String purpose, String emailHash) {
        return PREFIX + "code:" + purpose + ":" + emailHash;
    }

    private String codeHash(String email, String purpose, String code) {
        return cryptoService.blindIndex(email + ":" + purpose + ":" + code);
    }

    private String subject(String purpose) {
        return switch (purpose) {
            case REGISTER -> "智慧打印 - 注册验证码";
            case RESET_PASSWORD -> "智慧打印 - 找回密码验证码";
            case BIND_EMAIL -> "智慧打印 - 绑定QQ邮箱验证码";
            case ADMIN_REGISTER -> "智慧打印 - 管理员邮箱验证码";
            default -> "智慧打印 - 邮箱验证码";
        };
    }
}
