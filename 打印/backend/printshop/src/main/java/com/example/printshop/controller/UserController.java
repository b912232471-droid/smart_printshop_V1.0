package com.example.printshop.controller;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.User;
import com.example.printshop.security.AuthContext;
import com.example.printshop.security.AuthPrincipal;
import com.example.printshop.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public User getUser(@PathVariable Integer id) {
        requireSelfOrAdmin(id);
        return publicUser(userService.getUserById(id));
    }

    @PutMapping("/")
    public int updateUser(@RequestBody User user) {
        requireSelfOrAdmin(user.getId());
        User existing = userService.getUserById(user.getId());
        if (existing == null) {
            throw ApiException.notFound("用户不存在");
        }
        user.setRegisterTime(existing.getRegisterTime());
        user.setLastLoginTime(existing.getLastLoginTime());
        return userService.updateUser(user);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(@PathVariable Integer id) {
        AuthContext.requireAdmin();
        Map<String, Object> response = new HashMap<>();
        
        // 1. 检查是否是admin账户（假设admin的ID是1，或者通过username判断）
        User user = userService.getUserById(id);
        if (user == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return response;
        }
        
        // 2. 保护admin账户（通过ID或username判断）
        if (id == 1 || "admin".equals(user.getUsername())) {
            response.put("success", false);
            response.put("message", "无法删除管理员账户");
            return response;
        }
        
        // 3. 执行删除
        int result = userService.deleteUser(id);
        response.put("success", result > 0);
        response.put("message", result > 0 ? "删除成功" : "删除失败");
        return response;
    }

    @GetMapping("/")
    public List<User> getAllUsers() {
        AuthContext.requireAdmin();
        return userService.getAllUsers().stream()
                .map(this::publicUser)
                .toList();
    }

    private User publicUser(User source) {
        if (source == null) {
            return null;
        }
        User user = new User();
        user.setId(source.getId());
        user.setUsername(source.getUsername());
        user.setPhone(source.getPhone());
        user.setAvatarUrl(source.getAvatarUrl());
        user.setRegisterTime(source.getRegisterTime());
        user.setLastLoginTime(source.getLastLoginTime());
        return user;
    }

    private void requireSelfOrAdmin(Integer userId) {
        AuthPrincipal principal = AuthContext.get();
        if (principal.isAdmin()) {
            return;
        }
        if (!principal.isUser() || userId == null || !userId.equals(principal.getId())) {
            throw ApiException.forbidden("cannot access another user's data");
        }
    }
}
