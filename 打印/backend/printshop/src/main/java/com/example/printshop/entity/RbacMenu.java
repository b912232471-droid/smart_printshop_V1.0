package com.example.printshop.entity;

import lombok.Data;

@Data
public class RbacMenu {
    private Integer id;
    private Integer parentId;
    private String menuName;
    private String menuType;
    private String perms;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Integer visible;
}
