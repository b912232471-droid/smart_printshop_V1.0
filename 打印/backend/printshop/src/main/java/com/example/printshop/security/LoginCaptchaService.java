package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class LoginCaptchaService {
    private static final String KEY_PREFIX = "printshop:login:captcha:";
    private static final char[] CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int WIDTH = 132;
    private static final int HEIGHT = 44;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random;
    private final long expireSeconds;

    @Autowired
    public LoginCaptchaService(StringRedisTemplate redisTemplate,
                               @Value("${printshop.security.captcha.expire-seconds:120}") long expireSeconds) {
        this(redisTemplate, expireSeconds, new SecureRandom());
    }

    LoginCaptchaService(StringRedisTemplate redisTemplate, long expireSeconds, SecureRandom random) {
        this.redisTemplate = redisTemplate;
        this.expireSeconds = Math.max(30, expireSeconds);
        this.random = random;
    }

    public Captcha create() {
        String id = UUID.randomUUID().toString();
        String answer = randomAnswer();
        try {
            redisTemplate.opsForValue().set(key(id), answer, Duration.ofSeconds(expireSeconds));
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("验证码服务暂不可用，请稍后重试");
        }
        return new Captcha(id, render(answer), expireSeconds);
    }

    public void verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw ApiException.badRequest("请输入验证码");
        }
        String expected;
        try {
            expected = redisTemplate.opsForValue().getAndDelete(key(captchaId.trim()));
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("验证码服务暂不可用，请稍后重试");
        }
        if (expected == null || !expected.equals(captchaCode.trim().toUpperCase(Locale.ROOT))) {
            throw ApiException.badRequest("验证码错误或已过期，请刷新后重试");
        }
    }

    private String randomAnswer() {
        StringBuilder answer = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            answer.append(CHARACTERS[random.nextInt(CHARACTERS.length)]);
        }
        return answer.toString();
    }

    private String render(String answer) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 248, 255));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 7; i++) {
                graphics.setColor(randomColor(120, 205));
                graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            for (int i = 0; i < answer.length(); i++) {
                graphics.setColor(randomColor(25, 115));
                graphics.drawString(String.valueOf(answer.charAt(i)), 13 + i * 29, 32 + random.nextInt(5) - 2);
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to render captcha", exception);
        }
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + random.nextInt(range), min + random.nextInt(range), min + random.nextInt(range));
    }

    private String key(String id) {
        return KEY_PREFIX + id;
    }

    public record Captcha(String captchaId, String image, long expiresIn) {
    }
}
