package com.example.printshop.mapper;

import com.example.printshop.entity.ImageGenSettings;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImageGenSettingsMapper {
    ImageGenSettings selectOne();

    int update(ImageGenSettings settings);
}
