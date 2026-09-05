package com.example.printshop.controller;

import com.example.printshop.common.ApiException;
import com.example.printshop.common.ApiResponse;
import com.example.printshop.entity.Admin;
import com.example.printshop.security.AdminLoginGuard;
import com.example.printshop.security.AuditService;
import com.example.printshop.security.AuthContext;
import com.example.printshop.security.AuthPrincipal;
import com.example.printshop.security.JwtService;
import com.example.printshop.security.LoginCaptchaService;
import com.example.printshop.security.QqMailVerificationService;
import com.example.printshop.service.AdminService;
import com.example.printshop.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    @Autowired
    private AdminService adminService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminLoginGuard adminLoginGuard;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private LoginCaptchaService captchaService;

    @Autowired
    private QqMailVerificationService mailVerificationService;
    
    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> params,
                                                  HttpServletRequest request) {
        String username = params.get("email");
        String password = params.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw ApiException.badRequest("用户名和密码不能为空");
        }

        captchaService.verify(params.get("captchaId"), params.get("captchaCode"));

        adminLoginGuard.checkAllowed(username, request);
        Admin admin = adminService.login(username, password);
        if (admin == null) {
            adminLoginGuard.recordFailure(username, request);
            auditService.record("admin_login", "failure", request, "username=" + username);
            throw ApiException.unauthorized("用户名或密码错误");
        }
        adminLoginGuard.recordSuccess(username, request);
        auditService.record("admin_login", "success", request, "username=" + username);

        JwtService.TokenPair token = jwtService.createAdminToken(admin);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token.token());
        data.put("expiresIn", token.expiresIn());
        data.put("admin", admin);
        return ApiResponse.ok(data);
    }
    
    /**
     * 注册新管理员
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Admin admin) {
        AuthContext.requireSuperAdmin();
        mailVerificationService.verify(admin.getEmail(), QqMailVerificationService.ADMIN_REGISTER, admin.getEmailCode());
        Map<String, Object> result = new HashMap<>();
        
        try {
            int rows = adminService.register(admin);
            
            if (rows > 0) {
                result.put("code", 200);
                result.put("message", "注册成功");
            } else {
                result.put("code", 500);
                result.put("message", "注册失败");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取所有管理员
     */
    @GetMapping("/")
    public List<Admin> getAll() {
        AuthContext.requireAdmin();
        List<Admin> admins = adminService.getAll();
        // 清除密码
        admins.forEach(admin -> admin.setPassword(null));
        return admins;
    }
    
    /**
     * 获取管理员详情
     */
    @GetMapping("/{id}")
    public Admin getById(@PathVariable Integer id) {
        AuthContext.requireAdmin();
        Admin admin = adminService.getById(id);
        if (admin != null) {
            admin.setPassword(null);  // 不返回密码
        }
        return admin;
    }
    
    /**
     * 更新管理员信息
     */
    @PutMapping("/")
    public int update(@RequestBody Admin admin) {
        AuthContext.requireSuperAdmin();
        // 不允许通过这个接口修改密码
        Admin existing = adminService.getById(admin.getId());
        if (existing == null) {
            throw ApiException.notFound("管理员不存在");
        }
        admin.setPassword(existing.getPassword());
        
        return adminService.update(admin);
    }
    
    /**
     * 删除管理员
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Integer id) {
        AuthContext.requireSuperAdmin();
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 获取要删除的管理员信息
            Admin admin = adminService.getById(id);
            
            if (admin == null) {
                result.put("code", 404);
                result.put("success", false);
                result.put("message", "管理员不存在");
                return result;
            }
            
            // 2. 保护超级管理员
            if ("superadmin".equals(admin.getRole())) {
                result.put("code", 403);
                result.put("success", false);
                result.put("message", "不能删除超级管理员");
                return result;
            }
            
            // 3. 保护admin账户（初始管理员）
            if ("admin".equals(admin.getUsername())) {
                result.put("code", 403);
                result.put("success", false);
                result.put("message", "不能删除初始管理员账户");
                return result;
            }
            
            // 4. 执行删除
            int rows = adminService.delete(id);
            result.put("code", 200);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            result.put("code", 500);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            AuthPrincipal principal = AuthContext.requireAdmin();
            Integer id = Integer.parseInt(params.get("id"));
            if (!principal.isSuperAdmin() && !principal.getId().equals(id)) {
                throw ApiException.forbidden("只能修改自己的密码");
            }
            String oldPassword = params.get("oldPassword");
            String newPassword = params.get("newPassword");
            
            int rows = adminService.changePassword(id, oldPassword, newPassword);
            
            if (rows > 0) {
                result.put("code", 200);
                result.put("message", "密码修改成功");
            } else {
                result.put("code", 500);
                result.put("message", "密码修改失败");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        
        return result;
    }

    @PostMapping("/accounts/{id}/reset-password")
    public ApiResponse<Void> resetAccountPassword(@PathVariable Integer id,
                                                   @RequestBody Map<String, String> params) {
        AuthContext.requireSuperAdmin();
        accountService.resetPassword(id, params == null ? null : params.get("newPassword"));
        return ApiResponse.ok(null);
    }
}


