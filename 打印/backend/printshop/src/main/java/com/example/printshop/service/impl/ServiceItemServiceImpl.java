package com.example.printshop.service.impl;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.ServiceItem;
import com.example.printshop.mapper.ServiceItemMapper;
import com.example.printshop.service.ServiceItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class ServiceItemServiceImpl implements ServiceItemService {
    @Autowired
    private ServiceItemMapper mapper;

    @Override
    public ServiceItem getById(Integer id) { return mapper.selectById(id);}
    @Override
    public List<ServiceItem> getAll() { return mapper.selectAll();}
    @Override
    public List<ServiceItem> getByCategory(String category) { return mapper.selectByCategory(category);}
    @Override
    public int add(ServiceItem item) {
        validate(item, false);
        return mapper.insert(item);
    }
    @Override
    public int update(ServiceItem item) {
        validate(item, true);
        return mapper.update(item);
    }
    @Override
    public int delete(Integer id) { return mapper.delete(id);}

    private void validate(ServiceItem item, boolean requireId) {
        if (item == null) {
            throw ApiException.badRequest("服务项目不能为空");
        }
        if (requireId && item.getId() == null) {
            throw ApiException.badRequest("服务项目ID不能为空");
        }
        item.setName(requiredText(item.getName(), "服务名称不能为空", 64));
        item.setCategory(optionalText(item.getCategory(), 32, "服务分类不能超过32位"));
        item.setDescription(optionalText(item.getDescription(), 255, "服务描述不能超过255位"));

        if (item.getPrice() == null || !Double.isFinite(item.getPrice())) {
            throw ApiException.badRequest("服务价格不合法");
        }
        BigDecimal price = BigDecimal.valueOf(item.getPrice());
        if (price.compareTo(BigDecimal.ZERO) <= 0 || price.scale() > 2) {
            throw ApiException.badRequest("服务价格不合法");
        }
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
