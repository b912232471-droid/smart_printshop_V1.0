package com.example.printshop.controller;

import com.example.printshop.entity.ServiceItem;
import com.example.printshop.security.AuthContext;
import com.example.printshop.service.ServiceItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service")
public class ServiceItemController {
    @Autowired
    private ServiceItemService service;

    @GetMapping("/")
    public List<ServiceItem> getAll() { return service.getAll(); }

    @GetMapping("/category/{category}")
    public List<ServiceItem> getByCategory(@PathVariable String category) { 
        return service.getByCategory(category); 
    }

    @GetMapping("/{id}")
    public ServiceItem getById(@PathVariable Integer id) { return service.getById(id); }

    @PostMapping("/")
    public int add(@RequestBody ServiceItem item) {
        AuthContext.requireAdmin();
        return service.add(item);
    }

    @PutMapping("/")
    public int update(@RequestBody ServiceItem item) {
        AuthContext.requireAdmin();
        return service.update(item);
    }

    @DeleteMapping("/{id}")
    public int delete(@PathVariable Integer id) {
        AuthContext.requireAdmin();
        return service.delete(id);
    }
}
