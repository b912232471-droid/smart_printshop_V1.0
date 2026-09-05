package com.example.printshop.entity;

import lombok.Data;

@Data
public class FileInfo {
    private Integer id;
    private Integer orderId;
    private String fileName;
    private String fileUrl;
    private String uploadTime;
}