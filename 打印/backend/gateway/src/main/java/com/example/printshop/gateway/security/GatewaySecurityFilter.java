package com.example.printshop.gateway.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GatewaySecurityFilter implements WebFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityFilter.class);
    private static final int MAX_COUNTERS = 50_000;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 60;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final GatewaySecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupEpochSecond = new AtomicLong();

    @Autowired
    public GatewaySecurityFilter(GatewaySecurityProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    GatewaySecurityFilter(GatewaySecurityProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();
        String clientIp = clientIp(exchange.getRequest());
        if (isRateLimited(exchange, clientIp)) {
            log.warn("gateway_rate_limited ip={} method={} path={}",
                    clientIp,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getRawPath());
            return writeTooManyRequests(exchange);
        }
        if (!isAuthorized(exchange)) {
            log.warn("gateway_unauthorized ip={} method={} path={}",
                    clientIp,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getRawPath());
            return writeUnauthorized(exchange);
        }
        return chain.filter(exchange).doFinally(signalType -> {
            if (properties.getRequestLog().isEnabled()) {
                long tookMs = (System.nanoTime() - startNanos) / 1_000_000L;
                HttpStatus status = HttpStatus.resolve(exchange.getResponse().getStatusCode() == null
                        ? 200
                        : exchange.getResponse().getStatusCode().value());
                log.info("gateway_request ip={} method={} path={} status={} tookMs={}",
                        clientIp,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getRawPath(),
                        status == null ? "-" : status.value(),
                        tookMs);
            }
        });
    }

    private boolean isRateLimited(ServerWebExchange exchange, String clientIp) {
        GatewaySecurityProperties.RateLimit rateLimit = properties.getRateLimit();
        if (!rateLimit.isEnabled()
                || rateLimit.getRequests() <= 0
                || rateLimit.getWindowSeconds() <= 0
                || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())
                || exchange.getRequest().getURI().getRawPath().startsWith("/actuator/")) {
            return false;
        }

        long now = clock.instant().getEpochSecond();
        cleanupExpiredCounters(now, rateLimit.getWindowSeconds());
        String key = clientIp + "|" + routeGroup(exchange.getRequest().getURI().getRawPath());
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.windowStartEpochSecond >= rateLimit.getWindowSeconds()) {
                return new WindowCounter(now, 1);
            }
            existing.count++;
            return existing;
        });
        return counter.count > rateLimit.getRequests();
    }

    private boolean isAuthorized(ServerWebExchange exchange) {
        if (!properties.getAuth().isEnabled()) {
            return true;
        }
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();
        if (!isApiPath(path) || isPublicRequest(request.getMethod(), path)) {
            return true;
        }
        String token = bearerToken(request);
        if (token == null || token.isBlank()) {
            return false;
        }
        return hasValidJwt(token);
    }

    private boolean isApiPath(String path) {
        return path != null && (path.equals("/api") || path.startsWith("/api/"));
    }

    private boolean isPublicRequest(HttpMethod method, String rawPath) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        String path = normalizedApiPath(rawPath);
        if (HttpMethod.POST.equals(method) && "/api/admin/login".equals(path)) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && "/api/auth/captcha".equals(path)) {
            return true;
        }
        if (HttpMethod.POST.equals(method)
                && ("/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/email/send".equals(path)
                || "/api/auth/password/reset".equals(path))) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && (path.startsWith("/api/service/") || "/api/service".equals(path))) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && "/api/store/active".equals(path)) {
            return true;
        }
        return HttpMethod.GET.equals(method) && path.startsWith("/api/public-files/");
    }

    private String normalizedApiPath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        if (rawPath.equals("/api/print")) {
            return "/api";
        }
        if (rawPath.startsWith("/api/print/")) {
            return "/api/" + rawPath.substring("/api/print/".length());
        }
        return rawPath;
    }

    private String bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private boolean hasValidJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            Map<String, Object> header = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), MAP_TYPE);
            if (!"HS256".equals(stringValue(header.get("alg"))) || !"JWT".equals(stringValue(header.get("typ")))) {
                return false;
            }

            String signed = parts[0] + "." + parts[1];
            String expected = base64Url(hmac(signed.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), MAP_TYPE);
            return hasValidClaims(claims);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasValidClaims(Map<String, Object> claims) {
        String type = stringValue(claims.get("type"));
        if (!"admin".equals(type) && !"user".equals(type)) {
            return false;
        }
        long id = numberValue(claims.get("id"));
        if (id <= 0) {
            return false;
        }
        String subject = stringValue(claims.get("sub"));
        if (!("%s:%d".formatted(type, id)).equals(subject)) {
            return false;
        }
        if (claims.get("exp") == null || claims.get("iat") == null || claims.get("jti") == null) {
            return false;
        }
        long issuedAt = numberValue(claims.get("iat"));
        long expiresAt = numberValue(claims.get("exp"));
        long now = Instant.now(clock).getEpochSecond();
        if (issuedAt <= 0 || issuedAt > now + ALLOWED_CLOCK_SKEW_SECONDS || expiresAt < issuedAt) {
            return false;
        }
        if (now >= expiresAt) {
            return false;
        }
        String jti = stringValue(claims.get("jti"));
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(jti);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return !isBlank(claims.get("role")) && !isBlank(claims.get("username"));
    }

    private byte[] hmac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(properties.getAuth().getJwtSecret().trim().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
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

    private void cleanupExpiredCounters(long now, int windowSeconds) {
        long last = lastCleanupEpochSecond.get();
        if (now - last < windowSeconds || !lastCleanupEpochSecond.compareAndSet(last, now)) {
            return;
        }
        Iterator<Map.Entry<String, WindowCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WindowCounter> entry = iterator.next();
            if (now - entry.getValue().windowStartEpochSecond >= windowSeconds || counters.size() > MAX_COUNTERS) {
                iterator.remove();
            }
        }
    }

    private String routeGroup(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] segments = normalized.split("/");
        if (segments.length >= 2 && "api".equals(segments[0])) {
            return "/api/" + segments[1];
        }
        return "/" + segments[0];
    }

    private String clientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (properties.isTrustForwardedHeaders() && forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private Mono<Void> writeTooManyRequests(ServerWebExchange exchange) {
        byte[] body = "{\"code\":429,\"message\":\"too many requests\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
        byte[] body = "{\"code\":401,\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static class WindowCounter {
        private final long windowStartEpochSecond;
        private int count;

        private WindowCounter(long windowStartEpochSecond, int count) {
            this.windowStartEpochSecond = windowStartEpochSecond;
            this.count = count;
        }
    }
}
