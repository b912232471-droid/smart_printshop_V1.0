package com.example.printshop.entity;

import lombok.Data;

@Data
public class ServiceItem {
    private Integer id;
    private String name;
    private String category;  // 服务分类：文档/照片/证件照/复印
    private Double price;
    private String description;
}