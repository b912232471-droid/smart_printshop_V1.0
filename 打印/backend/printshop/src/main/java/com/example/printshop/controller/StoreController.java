package com.example.printshop.controller;

import com.example.printshop.entity.Store;
import com.example.printshop.security.AuthContext;
import com.example.printshop.service.StoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/store")
public class StoreController {
    @Autowired
    private StoreService service;

    // 获取所有店铺
    @GetMapping("/")
    public List<Store> getAll() { 
        AuthContext.requireAdmin();
        return service.getAll(); 
    }

    // 获取营业中的店铺
    @GetMapping("/active")
    public List<Store> getActive() { 
        return service.getByStatus(1).stream()
                .map(this::publicStore)
                .toList();
    }

    // 获取单个店铺
    @GetMapping("/{id}")
    public Store getById(@PathVariable Integer id) { 
        AuthContext.requireAdmin();
        return service.getById(id); 
    }

    // 新增店铺（管理员功能）
    @PostMapping("/")
    public int add(@RequestBody Store store) { 
        AuthContext.requireAdmin();
        return service.add(store); 
    }

    // 更新店铺（管理员功能）
    @PutMapping("/")
    public int update(@RequestBody Store store) { 
        AuthContext.requireAdmin();
        return service.update(store); 
    }

    // 删除店铺（管理员功能）
    @DeleteMapping("/{id}")
    public int delete(@PathVariable Integer id) { 
        AuthContext.requireAdmin();
        return service.delete(id); 
    }

    private Store publicStore(Store source) {
        Store store = new Store();
        store.setId(source.getId());
        store.setName(source.getName());
        store.setShortName(source.getShortName());
        store.setAddress(source.getAddress());
        store.setImageUrl(source.getImageUrl());
        store.setLatitude(source.getLatitude());
        store.setLongitude(source.getLongitude());
        store.setPhone(source.getPhone());
        store.setHours(source.getHours());
        store.setServices(source.getServices());
        store.setStatus(source.getStatus());
        store.setSortOrder(source.getSortOrder());
        return store;
    }
}






