package com.example.printshop.entity;

import lombok.Data;

@Data
public class ImageGenTemplate {
    private Integer id;
    private String templateKey;
    private String title;
    private String category;
    private String promptTemplate;
    private String recommendedModel;
    private String recommendedSize;
    private String fieldsJson;
    private Integer sortOrder;
    private Integer status;
}
