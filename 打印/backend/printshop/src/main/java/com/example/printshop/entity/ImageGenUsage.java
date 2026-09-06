package com.example.printshop.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ImageGenUsage {
    private String statDate;
    private String modelId;
    private Long genCount;
    private BigDecimal totalCost;
    private Long totalTokens;
}
