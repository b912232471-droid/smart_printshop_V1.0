package com.example.printshop.entity;

import lombok.Data;

import java.util.Date;

@Data
public class OcrRecord {
    private Long id;
    private Long accountId;
    private String username;
    private String fileName;
    private Long fileSize;
    private Integer charCount;
    private Integer durationMs;
    private Integer status;
    private String failureReason;
    private Date createdAt;
}
