package com.example.printshop.service;

import com.example.printshop.entity.Admin;
import java.util.List;

public interface AdminService {
    Admin getById(Integer id);
    Admin getByUsername(String username);
    List<Admin> getAll();
    int register(Admin admin);
    int update(Admin admin);
    int delete(Integer id);
    Admin login(String username, String password);
    int changePassword(Integer id, String oldPassword, String newPassword);
}


