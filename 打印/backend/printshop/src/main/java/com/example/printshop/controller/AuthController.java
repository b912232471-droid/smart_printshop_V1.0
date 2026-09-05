package com.example.printshop.controller;

import com.example.printshop.common.ApiException;
import com.example.printshop.common.ApiResponse;
import com.example.printshop.entity.Account;
import com.example.printshop.entity.User;
import com.example.printshop.security.AdminLoginGuard;
import com.example.printshop.security.AuthContext;
import com.example.printshop.security.JwtService;
import com.example.printshop.security.LoginCaptchaService;
import com.example.printshop.security.QqEmailAddress;
import com.example.printshop.security.QqMailVerificationService;
import com.example.printshop.service.AccountService;
import com.example.printshop.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accountService;
    private final UserService userService;
    private final JwtService jwtService;
    private final AdminLoginGuard loginGuard;
    private final LoginCaptchaService captchaService;
    private final QqMailVerificationService mailVerificationService;

    public AuthController(AccountService accountService,
                          UserService userService,
                          JwtService jwtService,
                          AdminLoginGuard loginGuard,
                          LoginCaptchaService captchaService,
                          QqMailVerificationService mailVerificationService) {
        this.accountService = accountService;
        this.userService = userService;
        this.jwtService = jwtService;
        this.loginGuard = loginGuard;
        this.captchaService = captchaService;
        this.mailVerificationService = mailVerificationService;
    }

    @GetMapping("/captcha")
    public ApiResponse<LoginCaptchaService.Captcha> captcha() {
        return ApiResponse.ok(captchaService.create());
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String email = value(request, "email");
        mailVerificationService.verify(email, QqMailVerificationService.REGISTER, value(request, "emailCode"));
        Account account = accountService.registerUser(
                email,
                value(request, "password"),
                value(request, "displayName"));
        return loginResponse(account);
    }

    @PostMapping("/email/send")
    public ApiResponse<Map<String, Object>> sendEmailCode(@RequestBody Map<String, String> request) {
        captchaService.verify(value(request, "captchaId"), value(request, "captchaCode"));
        long expiresIn = mailVerificationService.send(value(request, "email"), value(request, "purpose"));
        return ApiResponse.ok(Map.of("expiresIn", expiresIn));
    }

    @PostMapping("/email/bind")
    public ApiResponse<Void> bindEmail(@RequestBody Map<String, String> request) {
        Integer accountId = AuthContext.get().getId();
        String email = value(request, "email");
        mailVerificationService.verify(email, QqMailVerificationService.BIND_EMAIL, value(request, "emailCode"));
        accountService.bindQqEmail(accountId, email, value(request, "password"));
        return ApiResponse.ok(null);
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, String> request) {
        String email = value(request, "email");
        mailVerificationService.verify(email, QqMailVerificationService.RESET_PASSWORD, value(request, "emailCode"));
        Account account = accountService.findByQqEmail(email);
        if (account == null) {
            throw ApiException.badRequest("邮箱验证码错误或已过期");
        }
        accountService.resetPassword(account.getId(), value(request, "newPassword"));
        return ApiResponse.ok(null);
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> request,
                                                   HttpServletRequest httpRequest) {
        String username = QqEmailAddress.normalize(value(request, "email"));
        String password = value(request, "password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw ApiException.badRequest("用户名和密码不能为空");
        }
        captchaService.verify(value(request, "captchaId"), value(request, "captchaCode"));
        loginGuard.checkAllowed(username, httpRequest);
        Account account = accountService.authenticate(username, password, "user");
        if (account == null) {
            loginGuard.recordFailure(username, httpRequest);
            throw ApiException.unauthorized("用户名或密码错误");
        }
        loginGuard.recordSuccess(username, httpRequest);
        return loginResponse(account);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> request) {
        Integer accountId = AuthContext.requireUser().getId();
        accountService.changePassword(accountId, value(request, "oldPassword"), value(request, "newPassword"));
        return ApiResponse.ok(null);
    }

    private ApiResponse<Map<String, Object>> loginResponse(Account account) {
        JwtService.TokenPair token = jwtService.createAccountToken(account);
        User user = userService.getUserById(account.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token.token());
        data.put("expiresIn", token.expiresIn());
        data.put("user", publicUser(user));
        return ApiResponse.ok(data);
    }

    private User publicUser(User source) {
        if (source == null) {
            return null;
        }
        User user = new User();
        user.setId(source.getId());
        user.setUsername(source.getUsername());
        user.setPhone(source.getPhone());
        user.setEmail(source.getEmail());
        user.setAvatarUrl(source.getAvatarUrl());
        return user;
    }

    private String value(Map<String, String> request, String key) {
        return request == null ? null : request.get(key);
    }
}
