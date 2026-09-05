package com.example.printshop.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class SecurityAlertService {
    private static final Logger alertLog = LoggerFactory.getLogger("SECURITY_ALERT");

    private final boolean enabled;
    private final String webhookUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public SecurityAlertService(RestTemplate restTemplate,
                                @Value("${printshop.security.alert.enabled:true}") boolean enabled,
                                @Value("${printshop.security.alert.webhook-url:}") String webhookUrl) {
        this.enabled = enabled;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.restTemplate = restTemplate;
    }

    SecurityAlertService(boolean enabled, String webhookUrl, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.restTemplate = restTemplate;
    }

    public void adminLoginLocked(String username, String clientIp, int failures, long lockSeconds) {
        if (!enabled) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "admin_login_locked");
        payload.put("severity", "high");
        payload.put("username", sanitize(username));
        payload.put("clientIp", sanitize(clientIp));
        payload.put("failures", failures);
        payload.put("lockSeconds", lockSeconds);
        payload.put("occurredAt", Instant.now().toString());

        alertLog.warn("event={} severity={} username={} clientIp={} failures={} lockSeconds={}",
                payload.get("event"),
                payload.get("severity"),
                payload.get("username"),
                payload.get("clientIp"),
                failures,
                lockSeconds);

        if (!webhookUrl.isBlank() && restTemplate != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    restTemplate.postForEntity(webhookUrl, payload, String.class);
                } catch (Exception ex) {
                    alertLog.warn("event=security_alert_webhook_failed webhook={} reason={}",
                            sanitize(webhookUrl),
                            sanitize(ex.getMessage()));
                }
            });
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
