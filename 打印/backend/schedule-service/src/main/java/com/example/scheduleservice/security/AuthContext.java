package com.example.scheduleservice.security;

import com.example.scheduleservice.common.ApiException;

public final class AuthContext {
    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthPrincipal get() {
        return CURRENT.get();
    }

    public static long requireUserId() {
        AuthPrincipal principal = CURRENT.get();
        if (principal == null || !principal.isUser()) {
            throw ApiException.unauthorized("unauthorized");
        }
        return principal.id();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
