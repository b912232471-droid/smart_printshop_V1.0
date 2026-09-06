package com.example.printshop.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ImageGenRecord {
    private Long id;
    private Integer accountId;
    private String token;
    private String modelId;
    private String prompt;
    private Integer polished;
    private String templateKey;
    private String fileUrl;
    private Integer width;
    private Integer height;
    private String size;
    private Integer usageTokens;
    private BigDecimal costAmount;
    private Integer durationMs;
    private Integer moderationStatus;
    private Integer status;
    private Integer orderId;
    private String createdAt;
}
