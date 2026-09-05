package com.example.printshop.mapper;

import com.example.printshop.entity.ServiceItem;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ServiceItemMapper {
    ServiceItem selectById(Integer id);
    List<ServiceItem> selectAll();
    List<ServiceItem> selectByCategory(String category);  // 按分类查询
    int insert(ServiceItem item);
    int update(ServiceItem item);
    int delete(Integer id);
}