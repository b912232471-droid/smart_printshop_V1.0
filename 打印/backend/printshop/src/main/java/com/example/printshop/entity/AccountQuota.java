package com.example.printshop.entity;

import lombok.Data;

@Data
public class AccountQuota {
    private Integer accountId;
    private Integer imagegenDailyLimit;
    private Integer ocrDailyLimit;
    private Long updatedBy;
    private String updatedAt;
}
