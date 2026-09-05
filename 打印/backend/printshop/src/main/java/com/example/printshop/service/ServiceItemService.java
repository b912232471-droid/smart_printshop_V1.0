package com.example.printshop.service;

import com.example.printshop.entity.ServiceItem;
import java.util.List;

public interface ServiceItemService {
    ServiceItem getById(Integer id);
    List<ServiceItem> getAll();
    List<ServiceItem> getByCategory(String category);  // 按分类查询
    int add(ServiceItem item);
    int update(ServiceItem item);
    int delete(Integer id);
}