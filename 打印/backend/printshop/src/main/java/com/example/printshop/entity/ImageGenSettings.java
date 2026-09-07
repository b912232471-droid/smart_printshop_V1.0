package com.example.printshop.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ImageGenSettings {
    private Integer id;
    private Integer enabled;
    private String defaultModel;
    private Integer dailyQuotaPerUser;
    private Integer maxPromptLength;
    private Integer polishEnabled;
    private Integer moderationEnabled;
    private Integer templatesEnabled;
    private Integer watermarkEnabled;
    private String watermarkText;
    private BigDecimal dailyCostCap;
    private Long updatedBy;
    private String updatedAt;
}
