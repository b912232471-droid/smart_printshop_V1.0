package com.example.printshop.service;

import com.example.printshop.entity.OrderInfo;
import java.util.List;

public interface OrderInfoService {
    OrderInfo getOrderById(Integer id);
    List<OrderInfo> getOrdersByUserId(Integer userId);
    List<OrderInfo> getOrdersByStatus(Integer userId, Integer status);
    int createOrder(OrderInfo info);
    int updateOrderStatus(Integer orderId, Integer status, String fetchCode);
    int deleteOrder(Integer id);
    List<OrderInfo> getAllOrders();
    int getQueueCount();  // 获取排队订单数量
    String generateQueueNumber();  // 生成排队号
    String generateFetchCode();    // 生成取件码
}