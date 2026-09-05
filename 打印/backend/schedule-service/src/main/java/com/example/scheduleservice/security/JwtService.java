package com.example.scheduleservice.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 60;
    private final ScheduleSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JwtService(ScheduleSecurityProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    JwtService(ScheduleSecurityProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Optional<AuthPrincipal> verify(String token) {
        try {
            String secret = properties.getAuth().getJwtSecret();
            if (secret == null || secret.trim().getBytes(StandardCharsets.UTF_8).length < 32) {
                return Optional.empty();
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            Map<String, Object> header = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), MAP_TYPE);
            if (!"HS256".equals(stringValue(header.get("alg"))) || !"JWT".equals(stringValue(header.get("typ")))) {
                return Optional.empty();
            }
            String signed = parts[0] + "." + parts[1];
            String expected = base64Url(hmac(signed.getBytes(StandardCharsets.UTF_8), secret.trim()));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), MAP_TYPE);
            return principalFrom(claims);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<AuthPrincipal> principalFrom(Map<String, Object> claims) {
        String type = stringValue(claims.get("type"));
        if (!"user".equals(type) && !"admin".equals(type)) {
            return Optional.empty();
        }
        long id = numberValue(claims.get("id"));
        if (id <= 0) {
            return Optional.empty();
        }
        String subject = stringValue(claims.get("sub"));
        if (!("%s:%d".formatted(type, id)).equals(subject)) {
            return Optional.empty();
        }
        long issuedAt = numberValue(claims.get("iat"));
        long expiresAt = numberValue(claims.get("exp"));
        long now = Instant.now(clock).getEpochSecond();
        if (issuedAt <= 0 || issuedAt > now + ALLOWED_CLOCK_SKEW_SECONDS || expiresAt < issuedAt || now >= expiresAt) {
            return Optional.empty();
        }
        try {
            UUID.fromString(stringValue(claims.get("jti")));
        } catch (Exception ex) {
            return Optional.empty();
        }
        if (isBlank(claims.get("role")) || isBlank(claims.get("username"))) {
            return Optional.empty();
        }
        return Optional.of(new AuthPrincipal(type, id, subject));
    }

    private byte[] hmac(byte[] data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data);
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
