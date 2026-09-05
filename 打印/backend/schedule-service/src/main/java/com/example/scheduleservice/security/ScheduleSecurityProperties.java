package com.example.scheduleservice.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConfigurationProperties(prefix = "schedule.security")
public class ScheduleSecurityProperties {
    private final Auth auth = new Auth();
    private String fieldEncryptionKey;

    public Auth getAuth() {
        return auth;
    }

    public String getFieldEncryptionKey() {
        return fieldEncryptionKey;
    }

    public void setFieldEncryptionKey(String fieldEncryptionKey) {
        this.fieldEncryptionKey = fieldEncryptionKey;
    }

    @PostConstruct
    public void validate() {
        if (auth.enabled && (auth.jwtSecret == null || auth.jwtSecret.trim().getBytes(StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalStateException("SCHEDULE_JWT_SECRET must contain at least 32 bytes when schedule auth is enabled");
        }
    }

    public static class Auth {
        private boolean enabled;
        private String jwtSecret;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }
    }
}
