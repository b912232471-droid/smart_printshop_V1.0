package com.example.printshop.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private final RequestIpResolver requestIpResolver;

    @Autowired
    public AuditService(RequestIpResolver requestIpResolver) {
        this.requestIpResolver = requestIpResolver;
    }

    public void record(String event, String result, String detail) {
        AuthPrincipal principal = AuthContext.currentOrNull();
        auditLog.info("event={} result={} actorType={} actorId={} actorName={} detail={}",
                sanitize(event),
                sanitize(result),
                principal == null ? "anonymous" : sanitize(principal.getType()),
                principal == null ? "" : String.valueOf(principal.getId()),
                principal == null ? "" : sanitize(principal.getUsername()),
                sanitize(detail));
    }

    public void record(String event, String result, HttpServletRequest request, String detail) {
        AuthPrincipal principal = AuthContext.currentOrNull();
        auditLog.info("event={} result={} actorType={} actorId={} actorName={} ip={} method={} path={} detail={}",
                sanitize(event),
                sanitize(result),
                principal == null ? "anonymous" : sanitize(principal.getType()),
                principal == null ? "" : String.valueOf(principal.getId()),
                principal == null ? "" : sanitize(principal.getUsername()),
                clientIp(request),
                request == null ? "" : sanitize(request.getMethod()),
                request == null ? "" : sanitize(request.getRequestURI()),
                sanitize(detail));
    }

    private String clientIp(HttpServletRequest request) {
        return request == null ? "" : sanitize(requestIpResolver.clientIp(request));
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
