package com.example.printshop.service;

import com.example.printshop.entity.Store;
import java.util.List;

public interface StoreService {
    Store getById(Integer id);
    List<Store> getAll();
    List<Store> getByStatus(Integer status);  // 获取营业中的店铺
    int add(Store store);
    int update(Store store);
    int delete(Integer id);
}






