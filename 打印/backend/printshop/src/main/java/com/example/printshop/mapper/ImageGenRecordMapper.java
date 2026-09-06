package com.example.printshop.mapper;

import com.example.printshop.entity.ImageGenRecord;
import com.example.printshop.entity.ImageGenUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ImageGenRecordMapper {
    int insert(ImageGenRecord record);

    ImageGenRecord selectById(@Param("id") Long id);

    int countByAccount(@Param("accountId") Integer accountId);

    List<ImageGenRecord> selectByAccount(@Param("accountId") Integer accountId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    int countAll(@Param("accountId") Integer accountId,
                 @Param("modelId") String modelId,
                 @Param("moderationStatus") Integer moderationStatus,
                 @Param("start") String start,
                 @Param("end") String end);

    List<ImageGenRecord> selectAll(@Param("accountId") Integer accountId,
                                   @Param("modelId") String modelId,
                                   @Param("moderationStatus") Integer moderationStatus,
                                   @Param("start") String start,
                                   @Param("end") String end,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    int softDelete(@Param("id") Long id, @Param("accountId") Integer accountId);

    int bindOrder(@Param("id") Long id, @Param("accountId") Integer accountId, @Param("orderId") Integer orderId);

    BigDecimal sumCostSinceToday();

    List<ImageGenUsage> aggregateUsage(@Param("start") String start, @Param("end") String end);
}
