package com.example.printshop.mapper;

import com.example.printshop.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface OrderInfoMapper {
    OrderInfo selectById(Integer id);
    List<OrderInfo> selectByUserId(Integer userId);
    int insert(OrderInfo orderInfo);
    int update(OrderInfo orderInfo);
    int delete(Integer id);
    List<OrderInfo> selectByStatus(Integer userId, Integer status);
    List<OrderInfo> selectAll();
    int selectQueueCount();  // 查询排队订单数量（status=0或1）
    int selectTodayOrderCount();  // 查询今天的订单数量
}