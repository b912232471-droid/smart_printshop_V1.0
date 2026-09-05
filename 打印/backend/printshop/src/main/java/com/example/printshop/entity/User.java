package com.example.printshop.entity;

import lombok.Data;

@Data
public class User {
    private Integer id;
    private String username;
    private String phone;
    private String email;
    private String avatarUrl;  // 用户头像URL
    private String registerTime;
    private String lastLoginTime;
}
