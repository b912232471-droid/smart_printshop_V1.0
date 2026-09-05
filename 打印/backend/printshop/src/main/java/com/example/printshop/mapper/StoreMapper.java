package com.example.printshop.mapper;

import com.example.printshop.entity.Store;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface StoreMapper {
    Store selectById(Integer id);
    List<Store> selectAll();
    List<Store> selectByStatus(Integer status);  // 按状态查询（1营业中 0已关闭）
    int insert(Store store);
    int update(Store store);
    int delete(Integer id);
}






