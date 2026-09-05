package com.example.printshop.service;

import com.example.printshop.entity.Account;

public interface AccountService {
    Account registerUser(String email, String password, String displayName);
    Account authenticate(String email, String password, String requiredType);
    Account findByQqEmail(String email);
    boolean emailExists(String email);
    int bindQqEmail(Integer id, String email, String currentPassword);
    Account requireActive(Integer id);
    int changePassword(Integer id, String oldPassword, String newPassword);
    int resetPassword(Integer id, String newPassword);
}
