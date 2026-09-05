package com.example.printshop.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Store {
    private Integer id;
    private String name;
    private String shortName;
    private String deviceCode;  // 设备号
    private String address;
    private String imageUrl;    // 店铺图片URL
    private Double latitude;
    private Double longitude;
    private String phone;
    private String hours;
    private String services;  // 逗号分隔的服务列表
    private Integer status;   // 1营业中 0已关闭
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
}


