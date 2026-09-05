package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.Admin;
import com.example.printshop.entity.Account;
import com.example.printshop.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long adminExpireSeconds;
    private final long userExpireSeconds;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${printshop.jwt.secret}") String secret,
                      @Value("${printshop.jwt.admin-expire-seconds}") long adminExpireSeconds,
                      @Value("${printshop.jwt.user-expire-seconds}") long userExpireSeconds) {
        if (!isStrongSecret(secret)) {
            throw new IllegalStateException("PRINTSHOP_JWT_SECRET must be configured with at least 32 random characters");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.trim().getBytes(StandardCharsets.UTF_8);
        this.adminExpireSeconds = adminExpireSeconds;
        this.userExpireSeconds = userExpireSeconds;
    }

    public TokenPair createAdminToken(Admin admin) {
        Map<String, Object> claims = baseClaims("admin", admin.getId(), adminExpireSeconds);
        claims.put("role", admin.getRole());
        claims.put("username", admin.getUsername());
        claims.put("sub", "admin:" + admin.getId());
        return new TokenPair(sign(claims), adminExpireSeconds);
    }

    public TokenPair createAccountToken(Account account) {
        long expiresIn = "admin".equals(account.getAccountType()) ? adminExpireSeconds : userExpireSeconds;
        Map<String, Object> claims = baseClaims(account.getAccountType(), account.getId(), expiresIn);
        claims.put("role", account.getRole());
        claims.put("username", account.getUsername());
        claims.put("sub", account.getAccountType() + ":" + account.getId());
        return new TokenPair(sign(claims), expiresIn);
    }

    public TokenPair createUserToken(User user) {
        Map<String, Object> claims = baseClaims("user", user.getId(), userExpireSeconds);
        claims.put("role", "user");
        claims.put("username", user.getUsername());
        claims.put("sub", "user:" + user.getId());
        return new TokenPair(sign(claims), userExpireSeconds);
    }

    public AuthPrincipal parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw ApiException.unauthorized("invalid token");
            }

            Map<String, Object> header = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), MAP_TYPE);
            if (!"HS256".equals(stringValue(header.get("alg"))) || !"JWT".equals(stringValue(header.get("typ")))) {
                throw ApiException.unauthorized("invalid token header");
            }

            String signed = parts[0] + "." + parts[1];
            String expected = base64Url(hmac(signed.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw ApiException.unauthorized("invalid token signature");
            }

            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), MAP_TYPE);
            validateClaims(claims);
            long exp = numberValue(claims.get("exp"));
            if (Instant.now().getEpochSecond() >= exp) {
                throw ApiException.unauthorized("token expired");
            }

            AuthPrincipal principal = new AuthPrincipal();
            principal.setId((int) numberValue(claims.get("id")));
            principal.setSubject(stringValue(claims.get("sub")));
            principal.setType(stringValue(claims.get("type")));
            principal.setRole(stringValue(claims.get("role")));
            principal.setUsername(stringValue(claims.get("username")));
            return principal;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.unauthorized("invalid token");
        }
    }

    private Map<String, Object> baseClaims(String type, Integer id, long expireSeconds) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("typ", "JWT");
        claims.put("type", type);
        claims.put("id", id);
        claims.put("iat", now);
        claims.put("exp", now + expireSeconds);
        claims.put("jti", UUID.randomUUID().toString());
        return claims;
    }

    private String sign(Map<String, Object> claims) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String headerPart = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadPart = base64Url(objectMapper.writeValueAsBytes(claims));
            String signed = headerPart + "." + payloadPart;
            return signed + "." + base64Url(hmac(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign token", ex);
        }
    }

    private byte[] hmac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
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

    private void validateClaims(Map<String, Object> claims) {
        String type = stringValue(claims.get("type"));
        if (!"admin".equals(type) && !"user".equals(type)) {
            throw ApiException.unauthorized("invalid token type");
        }
        long id = numberValue(claims.get("id"));
        if (id <= 0) {
            throw ApiException.unauthorized("invalid token subject");
        }
        String subject = stringValue(claims.get("sub"));
        if (!("%s:%d".formatted(type, id)).equals(subject)) {
            throw ApiException.unauthorized("invalid token subject");
        }
        if (claims.get("exp") == null || claims.get("iat") == null || claims.get("jti") == null) {
            throw ApiException.unauthorized("invalid token claims");
        }
        long issuedAt = numberValue(claims.get("iat"));
        long expiresAt = numberValue(claims.get("exp"));
        long now = Instant.now().getEpochSecond();
        if (issuedAt <= 0 || issuedAt > now + ALLOWED_CLOCK_SKEW_SECONDS || expiresAt < issuedAt) {
            throw ApiException.unauthorized("invalid token claims");
        }
        String jti = stringValue(claims.get("jti"));
        if (jti == null || jti.isBlank()) {
            throw ApiException.unauthorized("invalid token claims");
        }
        try {
            UUID.fromString(jti);
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("invalid token claims");
        }
        if (isBlank(claims.get("role"))) {
            throw ApiException.unauthorized("invalid account token");
        }
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private boolean isStrongSecret(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase();
        return trimmed.length() >= 32
                && !lower.startsWith("change-me")
                && !lower.contains("dev-only")
                && !lower.contains("replace-with");
    }

    public record TokenPair(String token, long expiresIn) {
    }
}
