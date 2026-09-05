package com.example.printshop.security;

import com.example.printshop.common.ApiException;

import java.util.Locale;

public final class QqEmailAddress {
    private QqEmailAddress() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw ApiException.badRequest("请输入QQ邮箱");
        }
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[a-z0-9][a-z0-9._-]{2,63}@qq\\.com$")) {
            throw ApiException.badRequest("仅支持QQ邮箱（例如 123456@qq.com）");
        }
        return email;
    }

    public static boolean isQqEmail(String value) {
        try {
            normalize(value);
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }
}
