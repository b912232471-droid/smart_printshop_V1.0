package com.example.printshop.mapper;

import com.example.printshop.entity.OcrSettings;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OcrSettingsMapper {
    OcrSettings selectOne();

    int update(OcrSettings settings);
}
