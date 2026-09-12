package com.example.printshop.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class Account {
    private Integer id;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private String accountType;
    private String role;
    private Integer status;
    private String displayName;
    private String realName;
    private String phone;
    private String email;
    @JsonIgnore
    private String emailHash;
    private String avatarUrl;
    private String createdAt;
    private String lastLoginAt;
    private Integer imagegenDailyLimit;
    private Integer ocrDailyLimit;
}
