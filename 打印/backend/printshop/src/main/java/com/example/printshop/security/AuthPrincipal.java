package com.example.printshop.security;

public class AuthPrincipal {
    private Integer id;
    private String subject;
    private String type;
    private String role;
    private String username;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isAdmin() {
        return "admin".equals(type);
    }

    public boolean isUser() {
        return "user".equals(type);
    }

    public boolean isSuperAdmin() {
        return isAdmin() && "superadmin".equals(role);
    }
}
