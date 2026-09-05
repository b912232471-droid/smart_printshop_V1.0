package com.example.printshop.security;

import com.example.printshop.common.ApiException;

public final class AuthContext {
    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthPrincipal get() {
        AuthPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw ApiException.unauthorized("authentication required");
        }
        return principal;
    }

    public static AuthPrincipal currentOrNull() {
        return CURRENT.get();
    }

    public static AuthPrincipal requireUser() {
        AuthPrincipal principal = get();
        if (!principal.isUser()) {
            throw ApiException.forbidden("user permission required");
        }
        return principal;
    }

    public static AuthPrincipal requireAdmin() {
        AuthPrincipal principal = get();
        if (!principal.isAdmin()) {
            throw ApiException.forbidden("admin permission required");
        }
        return principal;
    }

    public static AuthPrincipal requireSuperAdmin() {
        AuthPrincipal principal = requireAdmin();
        if (!principal.isSuperAdmin()) {
            throw ApiException.forbidden("superadmin permission required");
        }
        return principal;
    }

    public static AuthPrincipal requirePermission(String permission) {
        AuthPrincipal principal = requireAdmin();
        if (principal.isSuperAdmin() || principal.hasPerm(permission)) {
            return principal;
        }
        throw ApiException.forbidden("permission denied: " + permission);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
