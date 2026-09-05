package com.example.scheduleservice.service.sync;

import com.example.scheduleservice.common.ApiException;
import com.example.scheduleservice.model.CourseSchedule;
import com.example.scheduleservice.model.JwAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ZfsoftScheduleSyncAdapter implements ScheduleSyncAdapter {
    private static final Pattern CSRF_PATTERN = Pattern.compile(
            "<input[^>]*(?:id|name)=\"csrftoken\"[^>]*value=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIPS_PATTERN = Pattern.compile(
            "id=\"tips\"[^>]*>([^<]{1,200})",
            Pattern.CASE_INSENSITIVE);

    private final boolean enabled;
    private final String baseUrl;
    private final String gnmkdm;
    private final int timeoutMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZfsoftScheduleSyncAdapter(
            @Value("${schedule.sync.zfsoft.enabled:false}") boolean enabled,
            @Value("${schedule.sync.zfsoft.base-url:}") String baseUrl,
            @Value("${schedule.sync.zfsoft.gnmkdm:N253508}") String gnmkdm,
            @Value("${schedule.sync.zfsoft.timeout-ms:15000}") int timeoutMillis) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.gnmkdm = gnmkdm == null || gnmkdm.isBlank() ? "N253508" : gnmkdm.trim();
        this.timeoutMillis = Math.max(1000, timeoutMillis);
    }

    @Override
    public boolean isConfigured() {
        return enabled && !baseUrl.isBlank();
    }

    @Override
    public List<CourseSchedule> fetchCourses(long userId, JwAccount account, String jwPassword, String xnm, String xqm) {
        URI base = baseUri();
        CookieManager cookieManager = new CookieManager();
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();

        String csrftoken = fetchCsrftoken(client, base);
        String encryptedPassword = encryptPassword(client, base, jwPassword);
        login(client, base, account.studentId(), encryptedPassword, csrftoken);
        JsonNode kbResponse = fetchKbResponse(client, base, xnm, xqm);
        return parseCourses(kbResponse, userId, account.studentId(), xnm, xqm);
    }

    private URI baseUri() {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("scheme");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw ApiException.serviceUnavailable("正方教务适配器地址无效");
        }
    }

    private String fetchCsrftoken(HttpClient client, URI base) {
        HttpResponse<String> response;
        try {
            response = client.send(request(base, "/xtgl/login_slogin.html", "GET", null), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            throw ApiException.serviceUnavailable("教务系统登录页访问失败");
        }
        if (response.statusCode() != 200) {
            throw ApiException.serviceUnavailable("教务系统登录页访问失败");
        }
        Matcher matcher = CSRF_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            throw ApiException.serviceUnavailable("教务系统登录页缺少csrftoken");
        }
        return matcher.group(1);
    }

    private String encryptPassword(HttpClient client, URI base, String plainPassword) {
        HttpResponse<String> response;
        try {
            response = client.send(
                    request(base, "/xtgl/login_getPublicKey.html", "GET", null),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            throw ApiException.serviceUnavailable("教务系统公钥获取失败");
        }
        JsonNode key;
        try {
            key = objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw ApiException.serviceUnavailable("教务系统公钥响应无效");
        }
        BigInteger modulus = parseKeyNumber(key.path("modulus").asText(""));
        BigInteger exponent = parseKeyNumber(key.path("exponent").asText(""));
        if (modulus.signum() <= 0 || exponent.signum() <= 0) {
            throw ApiException.serviceUnavailable("教务系统公钥响应无效");
        }
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw ApiException.serviceUnavailable("教务密码加密失败");
        }
    }

    private BigInteger parseKeyNumber(String value) {
        if (value.isBlank()) {
            return BigInteger.ZERO;
        }
        try {
            return new BigInteger(1, Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException ex) {
            try {
                return new BigInteger(value, 16);
            } catch (NumberFormatException ex2) {
                return BigInteger.ZERO;
            }
        }
    }

    private void login(HttpClient client, URI base, String studentId, String encryptedPassword, String csrftoken) {
        String form = formBody()
                .add("yhm", studentId)
                .add("mm", encryptedPassword)
                .add("csrftoken", csrftoken)
                .add("language", "zh_CN")
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request(base, "/xtgl/login_slogin.html", "POST", form), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            throw ApiException.serviceUnavailable("教务系统登录失败");
        }
        String finalUrl = response.uri().toString();
        if (finalUrl.contains("login_slogin")) {
            String tips = extractTips(response.body());
            throw ApiException.badRequest("教务账号登录失败" + (tips == null ? "" : "：" + tips));
        }
    }

    private JsonNode fetchKbResponse(HttpClient client, URI base, String xnm, String xqm) {
        String path = "/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=" + URLEncoder.encode(gnmkdm, StandardCharsets.UTF_8);
        String form = formBody()
                .add("xnm", mapXnm(xnm))
                .add("xqm", mapXqm(xqm))
                .add("kzlx", "ck")
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request(base, path, "POST", form), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            throw ApiException.serviceUnavailable("教务课表查询失败");
        }
        if (response.statusCode() != 200) {
            throw ApiException.serviceUnavailable("教务课表查询失败");
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw ApiException.serviceUnavailable("教务课表响应无效");
        }
    }

    private List<CourseSchedule> parseCourses(JsonNode response, long userId, String studentId, String xnm, String xqm) {
        JsonNode kbList = response == null ? null : response.get("kbList");
        if (kbList == null || !kbList.isArray()) {
            return List.of();
        }
        List<CourseSchedule> courses = new ArrayList<>();
        for (JsonNode node : kbList) {
            String kcmc = text(node, "kcmc", "KCMC");
            String xqj = text(node, "xqj", "XQJ");
            String jcs = text(node, "jcs", "JCS");
            if (kcmc.isBlank() || xqj.isBlank() || jcs.isBlank()) {
                continue;
            }
            courses.add(new CourseSchedule(
                    0,
                    userId,
                    studentId,
                    truncate(xnm, 16),
                    truncate(xqm, 16),
                    truncate(kcmc, 255),
                    truncate(xqj, 32),
                    truncate(jcs, 64),
                    truncate(text(node, "cdmc", "CDMC"), 255),
                    truncate(text(node, "xm", "XM"), 128)
            ));
        }
        return courses;
    }

    private String text(JsonNode node, String lowerField, String upperField) {
        JsonNode value = node.get(lowerField);
        if (value == null || value.isNull() || value.asText("").isBlank()) {
            value = node.get(upperField);
        }
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private String mapXnm(String xnm) {
        String value = xnm == null ? "" : xnm.trim();
        if (value.matches("\\d{4}-\\d{4}")) {
            return value.substring(0, 4);
        }
        return value;
    }

    private String mapXqm(String xqm) {
        String value = xqm == null ? "" : xqm.trim();
        return switch (value) {
            case "1" -> "3";
            case "2" -> "12";
            case "3" -> "16";
            default -> value;
        };
    }

    private String extractTips(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = TIPS_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    private HttpRequest request(URI base, String path, String method, String form) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(base.resolve(path))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36")
                .header("Accept", "application/json, text/html, */*");
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            builder.POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private FormBodyBuilder formBody() {
        return new FormBodyBuilder();
    }

    private static final class FormBodyBuilder {
        private final StringBuilder builder = new StringBuilder();

        FormBodyBuilder add(String name, String value) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(name, StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
            return this;
        }

        String build() {
            return builder.toString();
        }
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
