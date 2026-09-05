package com.example.scheduleservice.service.sync;

import com.example.scheduleservice.common.ApiException;
import com.example.scheduleservice.model.CourseSchedule;
import com.example.scheduleservice.model.JwAccount;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class HttpJsonScheduleSyncAdapter implements ScheduleSyncAdapter {
    private final boolean enabled;
    private final String endpointUrl;
    private final String bearerToken;
    private final int timeoutMillis;

    public HttpJsonScheduleSyncAdapter(
            @Value("${schedule.sync.http.enabled:false}") boolean enabled,
            @Value("${schedule.sync.http.endpoint-url:}") String endpointUrl,
            @Value("${schedule.sync.http.bearer-token:}") String bearerToken,
            @Value("${schedule.sync.http.timeout-ms:10000}") int timeoutMillis) {
        this.enabled = enabled;
        this.endpointUrl = endpointUrl == null ? "" : endpointUrl.trim();
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
        this.timeoutMillis = Math.max(1000, timeoutMillis);
    }

    @Override
    public boolean isConfigured() {
        return enabled && !endpointUrl.isBlank();
    }

    @Override
    public List<CourseSchedule> fetchCourses(long userId, JwAccount account, String jwPassword, String xnm, String xqm) {
        URI endpoint = endpointUri();
        HttpJsonSyncRequest request = new HttpJsonSyncRequest(
                account.studentId(),
                jwPassword,
                xnm,
                xqm
        );
        JsonNode response;
        try {
            response = restClient()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyAuth(headers, bearerToken))
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw ApiException.serviceUnavailable("教务同步适配器调用失败");
        }
        return parseCourses(response, userId, account.studentId(), xnm, xqm);
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(timeoutMillis);
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private URI endpointUri() {
        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (IllegalArgumentException ex) {
            throw ApiException.serviceUnavailable("教务同步适配器地址无效");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw ApiException.serviceUnavailable("教务同步适配器地址无效");
        }
        return uri;
    }

    private void applyAuth(HttpHeaders headers, String token) {
        if (!token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }

    private List<CourseSchedule> parseCourses(JsonNode response, long userId, String studentId, String xnm, String xqm) {
        if (response == null || !response.has("courses") || !response.get("courses").isArray()) {
            throw ApiException.serviceUnavailable("教务同步适配器响应无效");
        }
        List<CourseSchedule> courses = new ArrayList<>();
        for (JsonNode node : response.get("courses")) {
            String kcmc = requiredText(node, "kcmc");
            String xqj = requiredText(node, "xqj");
            String jcs = requiredText(node, "jcs");
            courses.add(new CourseSchedule(
                    0,
                    userId,
                    studentId,
                    truncate(xnm, 16),
                    truncate(xqm, 16),
                    truncate(kcmc, 255),
                    truncate(xqj, 32),
                    truncate(jcs, 64),
                    truncate(text(node, "cdmc", ""), 255),
                    truncate(text(node, "xm", ""), 128)
            ));
        }
        return courses;
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = text(node, fieldName, "");
        if (value.isBlank()) {
            throw ApiException.serviceUnavailable("教务同步适配器响应无效");
        }
        return value;
    }

    private String text(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.asText("").trim();
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private record HttpJsonSyncRequest(
            String studentId,
            String jwPassword,
            String xnm,
            String xqm
    ) {
    }
}
