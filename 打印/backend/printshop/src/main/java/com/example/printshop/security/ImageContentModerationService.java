package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Service
public class ImageContentModerationService {
    private final boolean enabled;
    private final String endpointUrl;
    private final String apiKey;
    private final boolean failClosed;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ImageContentModerationService(RestTemplateBuilder builder,
                                         ObjectMapper objectMapper,
                                         @Value("${printshop.security.image-moderation.enabled:false}") boolean enabled,
                                         @Value("${printshop.security.image-moderation.endpoint-url:}") String endpointUrl,
                                         @Value("${printshop.security.image-moderation.api-key:}") String apiKey,
                                         @Value("${printshop.security.image-moderation.timeout-ms:5000}") int timeoutMs,
                                         @Value("${printshop.security.image-moderation.fail-closed:true}") boolean failClosed) {
        this(
                builder
                        .connectTimeout(Duration.ofMillis(timeoutMs))
                        .readTimeout(Duration.ofMillis(timeoutMs))
                        .build(),
                objectMapper,
                enabled,
                endpointUrl,
                apiKey,
                failClosed
        );
    }

    public ImageContentModerationService(RestTemplate restTemplate,
                                         ObjectMapper objectMapper,
                                         boolean enabled,
                                         String endpointUrl,
                                         String apiKey,
                                         boolean failClosed) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.endpointUrl = endpointUrl == null ? "" : endpointUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.failClosed = failClosed;
    }

    public void moderate(MultipartFile file) {
        if (!enabled || file == null || file.isEmpty()) {
            return;
        }
        if (endpointUrl.isBlank()) {
            handleModerationFailure();
            return;
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(endpointUrl, requestEntity(file), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                handleModerationFailure();
                return;
            }
            Decision decision = decisionFrom(response.getBody());
            if (decision == Decision.BLOCK) {
                throw ApiException.badRequest("图片内容合规检测未通过");
            }
            if (decision == Decision.UNKNOWN) {
                handleModerationFailure();
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            handleModerationFailure();
        }
    }

    private HttpEntity<MultiValueMap<String, Object>> requestEntity(MultipartFile file) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (!apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                String name = file.getOriginalFilename();
                return name == null || name.isBlank() ? "image" : name;
            }
        });
        return new HttpEntity<>(body, headers);
    }

    private Decision decisionFrom(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return Decision.UNKNOWN;
        }
        JsonNode root = objectMapper.readTree(responseBody);
        return decisionFrom(root);
    }

    private Decision decisionFrom(JsonNode node) {
        if (node == null || node.isNull()) {
            return Decision.UNKNOWN;
        }
        if (node.isObject()) {
            Decision direct = directObjectDecision(node);
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Decision nested = decisionFrom(fields.next().getValue());
                direct = mergeDecision(direct, nested);
                if (direct == Decision.BLOCK) {
                    return direct;
                }
            }
            return direct;
        }
        if (node.isArray()) {
            Decision result = Decision.UNKNOWN;
            for (JsonNode item : node) {
                Decision nested = decisionFrom(item);
                result = mergeDecision(result, nested);
                if (result == Decision.BLOCK) {
                    return result;
                }
            }
            return result;
        }
        return Decision.UNKNOWN;
    }

    private Decision directObjectDecision(JsonNode node) {
        Decision result = Decision.UNKNOWN;
        result = mergeDecision(result, booleanDecision(node, "pass"));
        result = mergeDecision(result, booleanDecision(node, "passed"));
        result = mergeDecision(result, booleanDecision(node, "allowed"));
        result = mergeDecision(result, booleanDecision(node, "safe"));
        result = mergeDecision(result, stringDecision(node, "suggestion"));
        result = mergeDecision(result, stringDecision(node, "result"));
        result = mergeDecision(result, stringDecision(node, "decision"));
        return mergeDecision(result, stringDecision(node, "label"));
    }

    private Decision mergeDecision(Decision current, Decision next) {
        if (current == Decision.BLOCK || next == Decision.BLOCK) {
            return Decision.BLOCK;
        }
        if (current == Decision.PASS || next == Decision.PASS) {
            return Decision.PASS;
        }
        return Decision.UNKNOWN;
    }

    private Decision booleanDecision(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            return Decision.UNKNOWN;
        }
        return value.asBoolean() ? Decision.PASS : Decision.BLOCK;
    }

    private Decision stringDecision(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return Decision.UNKNOWN;
        }
        return switch (value.asText("").trim().toLowerCase(Locale.ROOT)) {
            case "pass", "allow", "allowed", "approve", "approved", "safe", "normal" -> Decision.PASS;
            case "block", "blocked", "reject", "rejected", "deny", "denied", "unsafe", "risk", "risky", "review" -> Decision.BLOCK;
            default -> Decision.UNKNOWN;
        };
    }

    private void handleModerationFailure() {
        if (failClosed) {
            throw ApiException.serviceUnavailable("图片内容合规检测服务不可用");
        }
    }

    private enum Decision {
        PASS,
        BLOCK,
        UNKNOWN
    }
}
