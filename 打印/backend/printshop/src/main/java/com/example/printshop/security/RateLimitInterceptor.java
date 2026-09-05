package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int maxRequests;
    private final long windowSeconds;
    private final Clock clock;
    private final AuditService auditService;
    private final RequestIpResolver requestIpResolver;

    @Autowired
    public RateLimitInterceptor(@Value("${printshop.security.rate-limit.enabled:true}") boolean enabled,
                                @Value("${printshop.security.rate-limit.requests:240}") int maxRequests,
                                @Value("${printshop.security.rate-limit.window-seconds:60}") long windowSeconds,
                                AuditService auditService,
                                RequestIpResolver requestIpResolver) {
        this(enabled, maxRequests, windowSeconds, Clock.systemUTC(), auditService, requestIpResolver);
    }

    RateLimitInterceptor(boolean enabled, int maxRequests, long windowSeconds, Clock clock, AuditService auditService) {
        this(enabled, maxRequests, windowSeconds, clock, auditService, new RequestIpResolver(false));
    }

    RateLimitInterceptor(boolean enabled,
                         int maxRequests,
                         long windowSeconds,
                         Clock clock,
                         AuditService auditService,
                         RequestIpResolver requestIpResolver) {
        this.enabled = enabled;
        this.maxRequests = Math.max(1, maxRequests);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.clock = clock;
        this.auditService = auditService;
        this.requestIpResolver = requestIpResolver == null ? new RequestIpResolver(false) : requestIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthContext.clear();
        if (!enabled || isSkipped(request)) {
            return true;
        }

        long now = clock.instant().getEpochSecond();
        String key = clientIp(request) + "|" + routeGroup(request);
        Bucket bucket = buckets.compute(key, (ignored, current) -> {
            if (current == null || current.windowStartEpochSecond + windowSeconds <= now) {
                return new Bucket(now, 1);
            }
            return new Bucket(current.windowStartEpochSecond, current.count + 1);
        });

        if (bucket.count > maxRequests) {
            if (auditService != null) {
                auditService.record("rate_limit", "blocked", request, "key=" + key + ",count=" + bucket.count);
            }
            throw ApiException.tooManyRequests("请求过于频繁，请稍后再试");
        }
        return true;
    }

    private boolean isSkipped(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(method) || path.startsWith("/api/public-files/");
    }

    private String routeGroup(HttpServletRequest request) {
        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI() == null ? "/" : request.getRequestURI();
        String[] parts = path.split("/");
        String group = parts.length >= 3 ? parts[2] : "root";
        return method + ":" + group;
    }

    private String clientIp(HttpServletRequest request) {
        return requestIpResolver.clientIp(request);
    }

    private record Bucket(long windowStartEpochSecond, int count) {
    }
}
