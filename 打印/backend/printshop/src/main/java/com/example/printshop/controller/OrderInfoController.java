package com.example.printshop.controller;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.OrderInfo;
import com.example.printshop.security.AuthContext;
import com.example.printshop.security.AuthPrincipal;
import com.example.printshop.service.OrderInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderInfoController {
    @Autowired
    private OrderInfoService orderInfoService;

    @GetMapping("/{id}")
    public OrderInfo getOrderById(@PathVariable Integer id) {
        OrderInfo order = requireOrderAccess(id);
        return order;
    }

    @GetMapping("/user/{userId}")
    public List<OrderInfo> getOrdersByUserId(@PathVariable Integer userId) {
        requireUserAccess(userId);
        return orderInfoService.getOrdersByUserId(userId);
    }

    @GetMapping("/user/{userId}/status/{status}")
    public List<OrderInfo> getOrdersByStatus(@PathVariable Integer userId, @PathVariable Integer status) {
        requireUserAccess(userId);
        return orderInfoService.getOrdersByStatus(userId, status);
    }

    @PostMapping("/")
    public Integer createOrder(@RequestBody OrderInfo info) {
        AuthPrincipal principal = AuthContext.get();
        if (principal.isUser()) {
            info.setUserId(principal.getId());
            info.setOrderStatus(0);
        } else if (!principal.isAdmin()) {
            throw ApiException.forbidden("cannot create order");
        }
        // 自动生成排队号和取件码
        String queueNumber = info.getQueueNumber();
        if (queueNumber == null || queueNumber.trim().isEmpty()) {
            info.setQueueNumber(orderInfoService.generateQueueNumber());
        }
        
        String fetchCode = info.getFetchCode();
        if (fetchCode == null || fetchCode.trim().isEmpty()) {
            info.setFetchCode(orderInfoService.generateFetchCode());
        }
        
        orderInfoService.createOrder(info);
        return info.getId();  // 返回自动生成的订单ID
    }

    @PostMapping("/{orderId}/status")
    public int updateOrderStatus(@PathVariable Integer orderId,
                                  @RequestParam Integer status,
                                  @RequestParam(required = false) String fetchCode) {
        AuthContext.requireAdmin();
        return orderInfoService.updateOrderStatus(orderId, status, fetchCode);
    }

    @PostMapping("/{orderId}/cancel")
    public int cancelOrder(@PathVariable Integer orderId) {
        OrderInfo order = requireOrderAccess(orderId);
        if (order.getOrderStatus() != null && order.getOrderStatus() > 0) {
            throw ApiException.badRequest("只能取消待处理订单");
        }
        return orderInfoService.updateOrderStatus(orderId, 4, null);
    }

    @DeleteMapping("/{id}")
    public int deleteOrder(@PathVariable Integer id) {
        AuthContext.requireAdmin();
        return orderInfoService.deleteOrder(id);
    }

    @GetMapping("/")
    public List<OrderInfo> getAllOrders() {
        AuthContext.requireAdmin();
        return orderInfoService.getAllOrders();
    }

    // 获取当前排队信息
    @GetMapping("/queue/count")
    public Integer getQueueCount() {
        // 统计状态为0(待处理)和1(打印中)的订单数量
        return orderInfoService.getQueueCount();
    }

    private OrderInfo requireOrderAccess(Integer orderId) {
        OrderInfo order = orderInfoService.getOrderById(orderId);
        if (order == null) {
            throw ApiException.notFound("订单不存在");
        }
        AuthPrincipal principal = AuthContext.get();
        if (principal.isAdmin()) {
            return order;
        }
        if (principal.isUser() && order.getUserId() != null && order.getUserId().equals(principal.getId())) {
            return order;
        }
        throw ApiException.forbidden("cannot access another user's order");
    }

    private void requireUserAccess(Integer userId) {
        AuthPrincipal principal = AuthContext.get();
        if (principal.isAdmin()) {
            return;
        }
        if (principal.isUser() && userId != null && userId.equals(principal.getId())) {
            return;
        }
        throw ApiException.forbidden("cannot access another user's orders");
    }
}
