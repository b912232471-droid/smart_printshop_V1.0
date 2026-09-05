package com.example.printshop.service.impl;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.OrderInfo;
import com.example.printshop.entity.ServiceItem;
import com.example.printshop.entity.Store;
import com.example.printshop.mapper.OrderInfoMapper;
import com.example.printshop.security.AuditService;
import com.example.printshop.service.OrderInfoService;
import com.example.printshop.service.ServiceItemService;
import com.example.printshop.service.StoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@Service
public class OrderInfoServiceImpl implements OrderInfoService {
    private static final int MAX_COPIES = 99;
    private static final int MAX_PAGE_COUNT = 500;
    private static final Set<String> COLOR_MODES = Set.of("BLACK_WHITE", "COLOR");
    private static final Set<String> PAPER_SIZES = Set.of("A4", "A3", "PHOTO_6IN", "ID_PHOTO");

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ServiceItemService serviceItemService;

    @Autowired
    private StoreService storeService;

    @Override
    public OrderInfo getOrderById(Integer id) {
        return orderInfoMapper.selectById(id);
    }

    @Override
    public List<OrderInfo> getOrdersByUserId(Integer userId) {
        return orderInfoMapper.selectByUserId(userId);
    }

    @Override
    public List<OrderInfo> getOrdersByStatus(Integer userId, Integer status) {
        return orderInfoMapper.selectByStatus(userId, status);
    }

    @Override
    public int createOrder(OrderInfo info) {
        if (info == null) {
            throw ApiException.badRequest("订单不能为空");
        }
        if (info.getOrderStatus() == null) {
            info.setOrderStatus(0);
        }
        if (info.getOrderStatus() != 0) {
            throw ApiException.badRequest("新订单状态必须为待处理");
        }
        validateCreateOrder(info);
        return orderInfoMapper.insert(info);
    }

    @Override
    public int updateOrderStatus(Integer orderId, Integer status, String fetchCode) {
        validateStatus(status);
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw ApiException.notFound("订单不存在");
        }
        Integer currentStatus = order.getOrderStatus();
        if (!canTransition(currentStatus, status)) {
            throw ApiException.badRequest("非法订单状态流转");
        }
        order.setOrderStatus(status);
        if (fetchCode != null && !fetchCode.isBlank()) {
            order.setFetchCode(fetchCode);
        }
        order.setUpdateTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        auditService.record("order_status_change", "success",
                "orderId=" + orderId + ",from=" + currentStatus + ",to=" + status);
        return orderInfoMapper.update(order);
    }

    @Override
    public int deleteOrder(Integer id) {
        return orderInfoMapper.delete(id);
    }

    @Override
    public List<OrderInfo> getAllOrders() {
        return orderInfoMapper.selectAll();
    }

    @Override
    public int getQueueCount() {
        return orderInfoMapper.selectQueueCount();
    }

    @Override
    public String generateQueueNumber() {
        // 获取今天的订单数量
        int todayCount = orderInfoMapper.selectTodayOrderCount();
        
        // 生成排队号：Q + 日期(YYYYMMDD) + 3位序号
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String dateStr = dateFormat.format(new Date());
        String sequence = String.format("%03d", todayCount + 1);
        
        return "Q" + dateStr + sequence;
    }

    @Override
    public String generateFetchCode() {
        // 生成6位纯数字取件码
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);  // 100000-999999
        return String.valueOf(code);
    }

    private void validateStatus(Integer status) {
        if (status == null || status < 0 || status > 4) {
            throw ApiException.badRequest("非法订单状态");
        }
    }

    private void validateCreateOrder(OrderInfo info) {
        if (info.getServiceId() == null) {
            throw ApiException.badRequest("服务项目不能为空");
        }
        ServiceItem serviceItem = serviceItemService.getById(info.getServiceId());
        if (serviceItem == null) {
            throw ApiException.badRequest("服务项目不存在");
        }

        if (info.getStoreId() == null) {
            throw ApiException.badRequest("门店不能为空");
        }
        Store store = storeService.getById(info.getStoreId());
        if (store == null) {
            throw ApiException.badRequest("门店不存在");
        }
        if (!Integer.valueOf(1).equals(store.getStatus())) {
            throw ApiException.badRequest("门店已关闭，不能下单");
        }

        BigDecimal servicePrice = validMoney(serviceItem.getPrice(), "服务单价不合法");
        normalizePrintOptions(info, serviceItem);
        BigDecimal totalPrice = servicePrice
                .multiply(BigDecimal.valueOf(info.getPageCount()))
                .multiply(BigDecimal.valueOf(info.getCopies()))
                .setScale(2, RoundingMode.HALF_UP);
        info.setTotalPrice(totalPrice.doubleValue());
    }

    private BigDecimal validMoney(Double value, String message) {
        if (value == null || !Double.isFinite(value)) {
            throw ApiException.badRequest(message);
        }
        BigDecimal money = BigDecimal.valueOf(value);
        if (money.compareTo(BigDecimal.ZERO) <= 0 || money.scale() > 2) {
            throw ApiException.badRequest(message);
        }
        return money;
    }

    private void normalizePrintOptions(OrderInfo info, ServiceItem serviceItem) {
        info.setCopies(validPositiveInt(info.getCopies(), 1, MAX_COPIES, "打印份数不合法"));
        info.setPageCount(validPositiveInt(info.getPageCount(), 1, MAX_PAGE_COUNT, "页数不合法"));

        Integer duplex = info.getDuplex();
        if (duplex == null) {
            duplex = 0;
        }
        if (duplex != 0 && duplex != 1) {
            throw ApiException.badRequest("单双面参数不合法");
        }
        info.setDuplex(duplex);

        info.setColorMode(normalizeColorMode(info.getColorMode(), serviceItem.getName()));
        info.setPaperSize(normalizePaperSize(info.getPaperSize(), serviceItem.getName()));
    }

    private int validPositiveInt(Integer value, int defaultValue, int maxValue, String message) {
        int normalized = value == null ? defaultValue : value;
        if (normalized < 1 || normalized > maxValue) {
            throw ApiException.badRequest(message);
        }
        return normalized;
    }

    private String normalizeColorMode(String value, String serviceName) {
        String normalized = normalizeCode(value);
        if (normalized == null) {
            normalized = serviceName != null && serviceName.contains("彩色") ? "COLOR" : "BLACK_WHITE";
        } else if ("BW".equals(normalized) || "BLACK".equals(normalized) || "BLACKWHITE".equals(normalized)) {
            normalized = "BLACK_WHITE";
        }
        if (!COLOR_MODES.contains(normalized)) {
            throw ApiException.badRequest("色彩参数不合法");
        }
        return normalized;
    }

    private String normalizePaperSize(String value, String serviceName) {
        String normalized = normalizeCode(value);
        if (normalized == null) {
            normalized = inferPaperSize(serviceName);
        } else if ("PHOTO".equals(normalized) || "PHOTO6IN".equals(normalized) || "6IN".equals(normalized)) {
            normalized = "PHOTO_6IN";
        } else if ("ID".equals(normalized) || "IDPHOTO".equals(normalized)) {
            normalized = "ID_PHOTO";
        }
        if (!PAPER_SIZES.contains(normalized)) {
            throw ApiException.badRequest("纸张规格不合法");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(" ", "_");
    }

    private String inferPaperSize(String serviceName) {
        if (serviceName == null) {
            return "A4";
        }
        if (serviceName.contains("A3")) {
            return "A3";
        }
        if (serviceName.contains("证件照")) {
            return "ID_PHOTO";
        }
        if (serviceName.contains("照片") || serviceName.contains("6寸")) {
            return "PHOTO_6IN";
        }
        return "A4";
    }

    private boolean canTransition(Integer currentStatus, Integer nextStatus) {
        if (currentStatus == null) {
            return nextStatus == 0;
        }
        if (currentStatus.equals(nextStatus)) {
            return true;
        }
        return switch (currentStatus) {
            case 0 -> nextStatus == 1 || nextStatus == 4;
            case 1 -> nextStatus == 2 || nextStatus == 4;
            case 2 -> nextStatus == 3;
            default -> false;
        };
    }
}
