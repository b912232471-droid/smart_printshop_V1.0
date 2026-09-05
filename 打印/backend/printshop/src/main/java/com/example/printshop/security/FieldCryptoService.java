package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class FieldCryptoService {
    private static final String PREFIX = "ENC:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldCryptoService(@Value("${printshop.crypto.field-key}") String fieldKey) {
        if (!isStrongKey(fieldKey)) {
            throw new IllegalStateException("PRINTSHOP_FIELD_ENCRYPTION_KEY must be configured with at least 32 random characters");
        }
        this.keySpec = new SecretKeySpec(sha256(fieldKey.trim()), "AES");
    }

    public String encryptNullable(String value) {
        if (value == null || value.isBlank() || value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt field", ex);
        }
    }

    public String decryptNullable(String value) {
        if (value == null || value.isBlank() || !value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] packed = Base64.getUrlDecoder().decode(value.substring(PREFIX.length()));
            if (packed.length <= IV_LENGTH) {
                throw ApiException.badRequest("invalid encrypted field");
            }
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("invalid encrypted field");
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String blindIndex(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySpec.getEncoded(), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not calculate blind index", ex);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize field encryption key", ex);
        }
    }

    private boolean isStrongKey(String value) {
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
}
