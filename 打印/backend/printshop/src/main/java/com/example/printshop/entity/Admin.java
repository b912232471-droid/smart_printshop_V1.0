package com.example.printshop.entity;

import lombok.Data;

@Data
public class Admin {
    private Integer id;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String emailHash;
    private String emailCode;
    private String role;         // superadmin/admin/operator
    private Integer status;      // 1启用 0禁用
    private String createTime;
    private String lastLoginTime;
}


