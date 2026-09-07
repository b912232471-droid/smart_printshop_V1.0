package com.example.printshop.entity;

import lombok.Data;

import java.util.Date;

@Data
public class OcrSettings {
    private Integer id;
    private Integer enabled;
    private Integer dailyQuotaPerUser;
    private Long updatedBy;
    private Date updatedAt;
}
