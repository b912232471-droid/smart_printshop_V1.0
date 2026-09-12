package com.example.printshop.security;

import java.util.Collections;
import java.util.Set;

public class AuthPrincipal {
    private Integer id;
    private String subject;
    private String type;
    private String role;
    private String username;
    private Set<String> perms = Collections.emptySet();

    public Set<String> getPerms() {
        return perms;
    }

    public void setPerms(Set<String> perms) {
        this.perms = perms == null ? Collections.emptySet() : perms;
    }

    public boolean hasPerm(String permission) {
        return permission != null && perms.contains(permission);
    }

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
