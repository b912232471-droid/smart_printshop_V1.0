package com.example.printshop.account;

import com.example.printshop.common.ApiException;
import com.example.printshop.common.ApiResponse;
import com.example.printshop.security.AuditService;
import com.example.printshop.security.AuthContext;
import com.example.printshop.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端账户管理接口（全部账户类型，user_account 视角）。
 */
@RestController
@RequestMapping("/api/account")
public class AccountAdminController {

    private final AccountManageService accountManageService;
    private final AuditService auditService;

    public AccountAdminController(AccountManageService accountManageService, AuditService auditService) {
        this.accountManageService = accountManageService;
        this.auditService = auditService;
    }

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String accountType,
                                                 @RequestParam(required = false) String role,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        AuthContext.requirePermission("print:user:list");
        return ApiResponse.ok(accountManageService.page(keyword, accountType, role, status, pageNum, pageSize));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Integer id,
                                          @RequestParam Integer status,
                                          HttpServletRequest request) {
        AuthPrincipal actor = AuthContext.requirePermission("print:user:update");
        accountManageService.updateStatus(id, status, actor);
        auditService.record("account_status", "success", request,
                "accountId=" + id + ",status=" + status + ",operator=" + actor.getId());
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/role")
    public ApiResponse<Void> updateRole(@PathVariable Integer id,
                                        @RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        AuthPrincipal actor = AuthContext.requirePermission("print:user:update");
        String role = body == null ? null : body.get("role");
        accountManageService.updateRole(id, role, actor);
        auditService.record("account_role", "success", request,
                "accountId=" + id + ",role=" + role + ",operator=" + actor.getId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Integer id,
                                           @RequestBody Map<String, String> body,
                                           HttpServletRequest request) {
        AuthPrincipal actor = AuthContext.requirePermission("print:user:resetPwd");
        accountManageService.resetPassword(id, body == null ? null : body.get("newPassword"), actor);
        auditService.record("account_reset_password", "success", request,
                "accountId=" + id + ",operator=" + actor.getId());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/quota")
    public ApiResponse<Map<String, Object>> quota(@PathVariable Integer id) {
        AuthPrincipal actor = AuthContext.requirePermission("print:user:quota");
        return ApiResponse.ok(accountManageService.quotaDetail(id, actor));
    }

    @PutMapping("/{id}/quota")
    public ApiResponse<Void> setQuota(@PathVariable Integer id,
                                      @RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        AuthPrincipal actor = AuthContext.requirePermission("print:user:quota");
        Integer imagegenLimit = intValue(body, "imagegenDailyLimit");
        Integer ocrLimit = intValue(body, "ocrDailyLimit");
        accountManageService.setQuota(id, imagegenLimit, ocrLimit, actor);
        auditService.record("account_quota", "success", request,
                "accountId=" + id + ",imagegen=" + imagegenLimit + ",ocr=" + ocrLimit + ",operator=" + actor.getId());
        return ApiResponse.ok(null);
    }

    private Integer intValue(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        if (value instanceof Number number) {
            if (number.doubleValue() != number.longValue()) {
                throw ApiException.badRequest("额度必须为整数");
            }
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return Integer.MIN_VALUE;
        }
    }
}
