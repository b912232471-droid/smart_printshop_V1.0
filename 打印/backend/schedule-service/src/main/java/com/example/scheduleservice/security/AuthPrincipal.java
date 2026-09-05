package com.example.scheduleservice.security;

public record AuthPrincipal(String type, long id, String subject) {
    public boolean isUser() {
        return "user".equals(type);
    }
}
