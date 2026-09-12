package com.example.printshop.entity;

import lombok.Data;

@Data
public class SysRole {
    private Integer id;
    private String roleKey;
    private String roleName;
    private Integer roleSort;
    private String dataScope;
    private Integer builtin;
    private Integer status;
    private String remark;
    private String createdAt;
    private String updatedAt;
    private Integer userCount;
}
