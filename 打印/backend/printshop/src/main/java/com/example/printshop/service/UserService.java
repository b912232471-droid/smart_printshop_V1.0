package com.example.printshop.service;

import com.example.printshop.entity.User;
import java.util.List;

public interface UserService {
    User getUserById(Integer id);
    int updateUser(User user);
    int deleteUser(Integer id);
    List<User> getAllUsers();
}
