package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;
    private final com.example.printshop.service.AccountService accountService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthInterceptor(JwtService jwtService, com.example.printshop.service.AccountService accountService) {
        this.jwtService = jwtService;
        this.accountService = accountService;
    }

    AuthInterceptor(JwtService jwtService) {
        this(jwtService, null);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AuthContext.clear();
        if (isPublicRequest(request)) {
            return true;
        }

        String token = bearerToken(request);
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("missing bearer token");
        }
        AuthPrincipal principal = jwtService.parse(token);
        if (accountService != null) {
            com.example.printshop.entity.Account account = accountService.requireActive(principal.getId());
            if (!account.getAccountType().equals(principal.getType())
                    || !account.getRole().equals(principal.getRole())
                    || !account.getUsername().equals(principal.getUsername())) {
                throw ApiException.unauthorized("account token is stale");
            }
            principal.setPerms(AuthPrincipal.parsePerms(account.getPerms()));
        }
        AuthContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/admin/login".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/auth/captcha".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method)
                && ("/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/email/send".equals(path)
                || "/api/auth/password/reset".equals(path))) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && (path.startsWith("/api/service/") || path.equals("/api/service"))) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/store/active".equals(path)) {
            return true;
        }
        return "GET".equalsIgnoreCase(method) && path.startsWith("/api/public-files/");
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
