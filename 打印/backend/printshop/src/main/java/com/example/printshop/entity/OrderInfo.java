package com.example.printshop.entity;

import lombok.Data;

@Data
public class OrderInfo {
    private Integer id;
    private Integer userId;
    private Integer serviceId;
    private Integer storeId;           // 店铺ID
    private Integer fileId;
    private String appointTime;
    private String queueNumber;
    private Double totalPrice;
    private Integer copies;            // 打印份数
    private Integer pageCount;         // 页数
    private Integer duplex;            // 0单面 1双面
    private String colorMode;          // BLACK_WHITE/COLOR
    private String paperSize;          // A4/A3/PHOTO_6IN/ID_PHOTO
    private Integer orderStatus;
    private String createTime;
    private String updateTime;
    private String fetchCode;
    
    // 扩展字段：服务信息（非数据库字段，用于前端显示）
    private String serviceName;        // 服务名称
    private Double servicePrice;       // 服务单价
    private String serviceDescription; // 服务描述
    
    // 扩展字段：用户信息（非数据库字段，用于前端显示）
    private String userName;           // 用户昵称
    private String userPhone;          // 用户手机号
    
    // 扩展字段：店铺信息（非数据库字段，用于前端显示）
    private String storeName;          // 店铺名称
    private String storeAddress;       // 店铺地址
}
