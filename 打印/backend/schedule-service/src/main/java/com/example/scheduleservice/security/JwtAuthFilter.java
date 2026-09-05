package com.example.scheduleservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final ScheduleSecurityProperties properties;
    private final JwtService jwtService;

    public JwtAuthFilter(ScheduleSecurityProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthContext.clear();
        try {
            if (!properties.getAuth().isEnabled()
                    || HttpMethod.OPTIONS.matches(request.getMethod())
                    || !isApiPath(request.getRequestURI())) {
                filterChain.doFilter(request, response);
                return;
            }
            String token = bearerToken(request);
            Optional<AuthPrincipal> principal = token == null ? Optional.empty() : jwtService.verify(token);
            if (principal.isEmpty()) {
                writeUnauthorized(response);
                return;
            }
            AuthContext.set(principal.get());
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean isApiPath(String path) {
        return path != null && (path.equals("/api") || path.startsWith("/api/"));
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        byte[] body = "{\"code\":401,\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(body);
    }
}
