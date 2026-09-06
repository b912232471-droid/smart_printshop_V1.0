package com.example.printshop.mapper;

import com.example.printshop.entity.ImageGenTemplate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImageGenTemplateMapper {
    List<ImageGenTemplate> selectActive();
}
