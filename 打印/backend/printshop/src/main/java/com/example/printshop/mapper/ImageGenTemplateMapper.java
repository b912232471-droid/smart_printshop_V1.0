package com.example.printshop.mapper;

import com.example.printshop.entity.ImageGenTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImageGenTemplateMapper {
    List<ImageGenTemplate> selectActive();

    List<ImageGenTemplate> selectAll();

    ImageGenTemplate selectById(@Param("id") Integer id);

    int countByKey(@Param("templateKey") String templateKey);

    int insert(ImageGenTemplate template);

    int update(ImageGenTemplate template);

    int deleteById(@Param("id") Integer id);
}
