package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AdminLoginGuard {
    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final long lockSeconds;
    private final long windowSeconds;
    private final Clock clock;
    private final RequestIpResolver requestIpResolver;
    private final SecurityAlertService securityAlertService;

    @Autowired
    public AdminLoginGuard(@Value("${printshop.security.login.max-failures:5}") int maxFailures,
                           @Value("${printshop.security.login.lock-seconds:900}") long lockSeconds,
                           @Value("${printshop.security.login.window-seconds:900}") long windowSeconds,
                           RequestIpResolver requestIpResolver,
                           SecurityAlertService securityAlertService) {
        this(maxFailures, lockSeconds, windowSeconds, Clock.systemUTC(), requestIpResolver, securityAlertService);
    }

    AdminLoginGuard(int maxFailures, long lockSeconds, long windowSeconds, Clock clock) {
        this(maxFailures, lockSeconds, windowSeconds, clock, new RequestIpResolver(false), null);
    }

    AdminLoginGuard(int maxFailures,
                    long lockSeconds,
                    long windowSeconds,
                    Clock clock,
                    RequestIpResolver requestIpResolver,
                    SecurityAlertService securityAlertService) {
        this.maxFailures = Math.max(1, maxFailures);
        this.lockSeconds = Math.max(60, lockSeconds);
        this.windowSeconds = Math.max(60, windowSeconds);
        this.clock = clock;
        this.requestIpResolver = requestIpResolver == null ? new RequestIpResolver(false) : requestIpResolver;
        this.securityAlertService = securityAlertService;
    }

    public void checkAllowed(String username, HttpServletRequest request) {
        String key = key(username, request);
        long now = now();
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }
        if (state.lockUntilEpochSecond > now) {
            throw ApiException.tooManyRequests("登录失败次数过多，请稍后再试");
        }
        if (isExpired(state, now)) {
            attempts.remove(key, state);
        }
    }

    public void recordFailure(String username, HttpServletRequest request) {
        String clientIp = clientIp(request);
        String key = key(username, clientIp);
        long now = now();
        LoginLockEvent[] lockEvent = new LoginLockEvent[1];
        attempts.compute(key, (ignored, state) -> {
            int failures = state == null || isExpired(state, now) ? 1 : state.failures + 1;
            long lockUntil = failures >= maxFailures ? now + lockSeconds : 0;
            if (lockUntil > 0 && (state == null || state.lockUntilEpochSecond <= now)) {
                lockEvent[0] = new LoginLockEvent(username, clientIp, failures);
            }
            return new AttemptState(failures, now, lockUntil);
        });
        if (lockEvent[0] != null && securityAlertService != null) {
            securityAlertService.adminLoginLocked(
                    lockEvent[0].username(),
                    lockEvent[0].clientIp(),
                    lockEvent[0].failures(),
                    lockSeconds
            );
        }
    }

    public void recordSuccess(String username, HttpServletRequest request) {
        attempts.remove(key(username, request));
    }

    private boolean isExpired(AttemptState state, long now) {
        return state.lockUntilEpochSecond <= now && state.lastFailureEpochSecond + windowSeconds <= now;
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    private String key(String username, HttpServletRequest request) {
        return key(username, clientIp(request));
    }

    private String key(String username, String clientIp) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalizedUsername + "|" + clientIp;
    }

    private String clientIp(HttpServletRequest request) {
        return requestIpResolver.clientIp(request);
    }

    private record AttemptState(int failures, long lastFailureEpochSecond, long lockUntilEpochSecond) {
    }

    private record LoginLockEvent(String username, String clientIp, int failures) {
    }
}
