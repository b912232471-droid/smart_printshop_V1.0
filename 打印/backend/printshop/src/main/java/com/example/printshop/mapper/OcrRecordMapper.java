package com.example.printshop.mapper;

import com.example.printshop.entity.OcrRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OcrRecordMapper {
    int insert(OcrRecord record);

    List<OcrRecord> selectAll(@Param("accountId") Long accountId,
                              @Param("status") Integer status,
                              @Param("start") String start,
                              @Param("end") String end,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    int countAll(@Param("accountId") Long accountId,
                 @Param("status") Integer status,
                 @Param("start") String start,
                 @Param("end") String end);

    Map<String, Object> aggregateUsage(@Param("start") String start,
                                       @Param("end") String end);
}
