package com.example.printshop.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImageGenSettingAuditMapper {
    int insert(@Param("adminId") Long adminId,
               @Param("beforeJson") String beforeJson,
               @Param("afterJson") String afterJson);
}
