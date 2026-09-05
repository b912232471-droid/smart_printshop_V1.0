package com.example.printshop.service.impl;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.Store;
import com.example.printshop.mapper.StoreMapper;
import com.example.printshop.service.StoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StoreServiceImpl implements StoreService {
    @Autowired
    private StoreMapper mapper;

    @Override
    public Store getById(Integer id) { return mapper.selectById(id);}
    @Override
    public List<Store> getAll() { return mapper.selectAll();}
    @Override
    public List<Store> getByStatus(Integer status) { return mapper.selectByStatus(status);}
    @Override
    public int add(Store store) {
        validate(store, false);
        return mapper.insert(store);
    }
    @Override
    public int update(Store store) {
        validate(store, true);
        return mapper.update(store);
    }
    @Override
    public int delete(Integer id) { return mapper.delete(id);}

    private void validate(Store store, boolean requireId) {
        if (store == null) {
            throw ApiException.badRequest("门店不能为空");
        }
        if (requireId && store.getId() == null) {
            throw ApiException.badRequest("门店ID不能为空");
        }
        store.setName(requiredText(store.getName(), "门店名称不能为空", 128));
        store.setShortName(optionalText(store.getShortName(), 64, "门店简称不能超过64位"));
        store.setDeviceCode(optionalText(store.getDeviceCode(), 64, "设备号不能超过64位"));
        store.setAddress(optionalText(store.getAddress(), 255, "门店地址不能超过255位"));
        store.setImageUrl(validImageUrl(store.getImageUrl()));
        store.setPhone(optionalText(store.getPhone(), 20, "门店电话不能超过20位"));
        store.setHours(optionalText(store.getHours(), 128, "营业时间不能超过128位"));
        store.setServices(optionalText(store.getServices(), 255, "服务列表不能超过255位"));

        if (store.getStatus() == null) {
            store.setStatus(1);
        }
        if (store.getStatus() != 0 && store.getStatus() != 1) {
            throw ApiException.badRequest("非法门店状态");
        }
        if (store.getSortOrder() == null) {
            store.setSortOrder(0);
        }
        validateCoordinate(store.getLatitude(), -90, 90, "门店纬度不合法", store.getStatus() == 1);
        validateCoordinate(store.getLongitude(), -180, 180, "门店经度不合法", store.getStatus() == 1);
    }

    private void validateCoordinate(Double value, double min, double max, String message, boolean required) {
        if (value == null) {
            if (required) {
                throw ApiException.badRequest(message);
            }
            return;
        }
        if (!Double.isFinite(value) || value < min || value > max) {
            throw ApiException.badRequest(message);
        }
    }

    private String validImageUrl(String value) {
        String normalized = optionalText(value, 512, "门店图片地址不合法");
        if (normalized == null) {
            return "/images/store-default.jpg";
        }
        if ("/images/store-default.jpg".equals(normalized)) {
            return normalized;
        }
        String prefix = "/api/public-files/stores/";
        if (!normalized.startsWith(prefix)) {
            throw ApiException.badRequest("门店图片地址不合法");
        }
        String filename = normalized.substring(prefix.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw ApiException.badRequest("门店图片地址不合法");
        }
        return normalized;
    }

    private String requiredText(String value, String message, int maxLength) {
        String normalized = optionalText(value, maxLength, message);
        if (normalized == null) {
            throw ApiException.badRequest(message);
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength, String message) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength || containsControlChar(normalized)) {
            throw ApiException.badRequest(message);
        }
        return normalized;
    }

    private boolean containsControlChar(String value) {
        return value.chars().anyMatch(ch -> Character.isISOControl(ch));
    }
}






