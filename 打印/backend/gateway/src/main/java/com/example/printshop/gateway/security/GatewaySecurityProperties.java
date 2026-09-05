package com.example.printshop.gateway.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {
    private final Auth auth = new Auth();
    private final RequestLog requestLog = new RequestLog();
    private final RateLimit rateLimit = new RateLimit();
    private boolean trustForwardedHeaders = false;

    public Auth getAuth() {
        return auth;
    }

    public RequestLog getRequestLog() {
        return requestLog;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    @PostConstruct
    void validate() {
        if (auth.enabled && !isStrongSecret(auth.jwtSecret)) {
            throw new IllegalStateException("GATEWAY_JWT_SECRET must be configured with at least 32 random characters when gateway auth is enabled");
        }
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

    public static class Auth {
        private boolean enabled = false;
        private String jwtSecret = "";

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

    public static class RequestLog {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int requests = 600;
        private int windowSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequests() {
            return requests;
        }

        public void setRequests(int requests) {
            this.requests = requests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
