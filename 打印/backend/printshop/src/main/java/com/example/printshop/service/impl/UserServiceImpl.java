package com.example.printshop.service.impl;

import com.example.printshop.entity.User;
import com.example.printshop.mapper.UserMapper;
import com.example.printshop.security.FieldCryptoService;
import com.example.printshop.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FieldCryptoService fieldCryptoService;

    @Override
    public User getUserById(Integer id) { return decrypt(userMapper.selectById(id)); }
    @Override
    public int updateUser(User user) {
        encrypt(user);
        int rows = userMapper.update(user);
        decrypt(user);
        return rows;
    }
    @Override
    public int deleteUser(Integer id) { return userMapper.delete(id); }
    @Override
    public List<User> getAllUsers() {
        List<User> users = userMapper.selectAll();
        users.forEach(this::decrypt);
        return users;
    }

    private void encrypt(User user) {
        if (user != null) {
            user.setPhone(fieldCryptoService.encryptNullable(user.getPhone()));
        }
    }

    private User decrypt(User user) {
        if (user != null) {
            user.setPhone(fieldCryptoService.decryptNullable(user.getPhone()));
            user.setEmail(fieldCryptoService.decryptNullable(user.getEmail()));
        }
        return user;
    }
}
