package com.example.printshop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {
    private final boolean requireHttps;
    private final boolean trustForwardedHeaders;

    public SecurityHeadersFilter(@Value("${printshop.security.require-https:false}") boolean requireHttps,
                                 @Value("${printshop.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.requireHttps = requireHttps;
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");

        if (requireHttps && !isHttps(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "HTTPS required");
            return;
        }

        if (isHttps(request)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isHttps(HttpServletRequest request) {
        return request.isSecure()
                || (trustForwardedHeaders && "https".equalsIgnoreCase(firstForwardedProto(request)));
    }

    private String firstForwardedProto(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto == null || forwardedProto.isBlank()) {
            return "";
        }
        return forwardedProto.split(",")[0].trim();
    }
}
