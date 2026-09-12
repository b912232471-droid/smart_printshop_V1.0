package com.example.printshop.account;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.Account;
import com.example.printshop.entity.AccountQuota;
import com.example.printshop.entity.SysRole;
import com.example.printshop.imagegen.ImageGenSettingsService;
import com.example.printshop.mapper.AccountMapper;
import com.example.printshop.mapper.RbacMapper;
import com.example.printshop.mapper.SysRoleMapper;
import com.example.printshop.ocr.OcrSettingsService;
import com.example.printshop.security.AuthPrincipal;
import com.example.printshop.security.FieldCryptoService;
import com.example.printshop.service.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 管理端账户管理：分页检索、角色分配、启禁用、重置密码、账户级额度。
 * 目标级防护（RBAC 审查 F-13）：非 superadmin 不得操作 superadmin 账户或签发 superadmin 角色。
 */
@Service
public class AccountManageService {
    private static final Pattern ROLE_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,31}$");
    private static final int MAX_PAGE_SIZE = 100;

    private final AccountMapper accountMapper;
    private final SysRoleMapper sysRoleMapper;
    private final RbacMapper rbacMapper;
    private final AccountService accountService;
    private final AccountQuotaService accountQuotaService;
    private final FieldCryptoService fieldCryptoService;
    private final ImageGenSettingsService imageGenSettingsService;
    private final OcrSettingsService ocrSettingsService;

    public AccountManageService(AccountMapper accountMapper,
                                SysRoleMapper sysRoleMapper,
                                RbacMapper rbacMapper,
                                AccountService accountService,
                                AccountQuotaService accountQuotaService,
                                FieldCryptoService fieldCryptoService,
                                ImageGenSettingsService imageGenSettingsService,
                                OcrSettingsService ocrSettingsService) {
        this.accountMapper = accountMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.rbacMapper = rbacMapper;
        this.accountService = accountService;
        this.accountQuotaService = accountQuotaService;
        this.fieldCryptoService = fieldCryptoService;
        this.imageGenSettingsService = imageGenSettingsService;
        this.ocrSettingsService = ocrSettingsService;
    }

    public Map<String, Object> page(String keyword, String accountType, String role, Integer status,
                                    int pageNum, int pageSize) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int current = Math.max(pageNum, 1);
        List<Account> records = accountMapper.selectAccountPage(
                trimmed, accountType, role, status, (current - 1) * size, size);
        records.forEach(account -> {
            account.setPhone(fieldCryptoService.decryptNullable(account.getPhone()));
            account.setEmail(fieldCryptoService.decryptNullable(account.getEmail()));
            account.setPasswordHash(null);
            account.setEmailHash(null);
        });
        long total = accountMapper.countAccountPage(trimmed, accountType, role, status);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("pageNum", current);
        data.put("pageSize", size);
        data.put("roles", sysRoleMapper.selectAll());
        data.put("defaults", globalDefaults());
        return data;
    }

    private Map<String, Object> globalDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("imagegenDailyLimit", imageGenSettingsService.get().getDailyQuotaPerUser());
        defaults.put("ocrDailyLimit", ocrSettingsService.get().getDailyQuotaPerUser());
        return defaults;
    }

    @Transactional
    public void updateRole(Integer targetId, String roleKey, AuthPrincipal actor) {
        if (roleKey == null || !ROLE_KEY_PATTERN.matcher(roleKey).matches()) {
            throw ApiException.badRequest("非法角色标识");
        }
        Account target = requireAccount(targetId);
        requireSuperAdminActor(actor, target, "修改超级管理员的角色");
        if (targetId.equals(actor.getId())) {
            throw ApiException.badRequest("不能修改当前登录账户的角色");
        }
        if ("superadmin".equals(roleKey) && !actor.isSuperAdmin()) {
            throw ApiException.forbidden("仅超级管理员可授予 superadmin 角色");
        }
        Integer roleId = rbacMapper.selectRoleIdByKey(roleKey);
        if (roleId == null) {
            throw ApiException.badRequest("角色不存在或已停用: " + roleKey);
        }
        accountMapper.updateRoleById(targetId, roleKey);
        rbacMapper.deleteUserRoles(targetId);
        rbacMapper.insertUserRole(targetId, roleId);
    }

    public void updateStatus(Integer targetId, Integer status, AuthPrincipal actor) {
        if (status == null || (status != 0 && status != 1)) {
            throw ApiException.badRequest("非法账户状态");
        }
        Account target = requireAccount(targetId);
        requireSuperAdminActor(actor, target, "修改超级管理员的状态");
        if (targetId.equals(actor.getId()) && status == 0) {
            throw ApiException.badRequest("不能禁用当前登录账户");
        }
        accountMapper.updateStatus(targetId, status);
    }

    public void resetPassword(Integer targetId, String newPassword, AuthPrincipal actor) {
        Account target = requireAccount(targetId);
        requireSuperAdminActor(actor, target, "重置超级管理员的密码");
        accountService.adminResetPassword(targetId, newPassword);
    }

    public Map<String, Object> quotaDetail(Integer targetId, AuthPrincipal actor) {
        requireAccount(targetId);
        AccountQuota quota = accountQuotaService.getQuota(targetId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountId", targetId);
        data.put("imagegenDailyLimit", quota == null ? null : quota.getImagegenDailyLimit());
        data.put("ocrDailyLimit", quota == null ? null : quota.getOcrDailyLimit());
        data.put("updatedBy", quota == null ? null : quota.getUpdatedBy());
        data.put("updatedAt", quota == null ? null : quota.getUpdatedAt());
        data.put("defaults", globalDefaults());
        return data;
    }

    public void setQuota(Integer targetId, Integer imagegenDailyLimit, Integer ocrDailyLimit, AuthPrincipal actor) {
        requireAccount(targetId);
        accountQuotaService.setQuota(targetId, imagegenDailyLimit, ocrDailyLimit,
                actor == null ? null : actor.getId().longValue());
    }

    private Account requireAccount(Integer targetId) {
        if (targetId == null) {
            throw ApiException.badRequest("账户ID不能为空");
        }
        Account target = accountMapper.selectById(targetId);
        if (target == null) {
            throw ApiException.notFound("账户不存在");
        }
        return target;
    }

    private void requireSuperAdminActor(AuthPrincipal actor, Account target, String action) {
        if ("superadmin".equals(target.getRole()) && (actor == null || !actor.isSuperAdmin())) {
            throw ApiException.forbidden("仅超级管理员可" + action);
        }
    }
}
