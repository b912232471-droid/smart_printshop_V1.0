package com.example.printshop.service.impl;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.Admin;
import com.example.printshop.mapper.AdminMapper;
import com.example.printshop.mapper.RbacMapper;
import com.example.printshop.security.FieldCryptoService;
import com.example.printshop.security.QqEmailAddress;
import com.example.printshop.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service
public class AdminServiceImpl implements AdminService {
    private static final Set<String> ALLOWED_ROLES = Set.of("superadmin", "admin", "operator");

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private RbacMapper rbacMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private FieldCryptoService fieldCryptoService;

    @Override
    public Admin getById(Integer id) {
        return decrypt(adminMapper.selectById(id));
    }
    
    @Override
    public Admin getByUsername(String username) {
        return decrypt(adminMapper.selectByUsername(username));
    }
    
    @Override
    public List<Admin> getAll() {
        List<Admin> admins = adminMapper.selectAll();
        admins.forEach(this::decrypt);
        return admins;
    }
    
    @Override
    @Transactional
    public int register(Admin admin) {
        validateAdminProfile(admin, false);
        validateNewPassword(admin.getPassword());
        String email = QqEmailAddress.normalize(admin.getEmail());
        String emailHash = fieldCryptoService.blindIndex(email);
        Admin existing = adminMapper.selectByEmailHash(emailHash);
        if (existing != null) {
            throw ApiException.badRequest("该QQ邮箱已存在");
        }
        admin.setUsername(email);
        admin.setEmail(email);
        admin.setEmailHash(emailHash);
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));

        // 默认角色和状态
        if (admin.getRole() == null) {
            admin.setRole("admin");
        }
        if (admin.getStatus() == null) {
            admin.setStatus(1);
        }

        encrypt(admin);
        int rows = adminMapper.insert(admin);
        if (rows > 0) {
            syncUserRole(admin.getId(), admin.getRole());
        }
        return rows;
    }

    @Override
    @Transactional
    public int update(Admin admin) {
        validateAdminProfile(admin, true);
        String email = QqEmailAddress.normalize(admin.getEmail());
        String emailHash = fieldCryptoService.blindIndex(email);
        Admin existing = adminMapper.selectByEmailHash(emailHash);
        if (existing != null && !existing.getId().equals(admin.getId())) {
            throw ApiException.badRequest("该QQ邮箱已存在");
        }
        admin.setUsername(email);
        admin.setEmail(email);
        admin.setEmailHash(emailHash);
        encrypt(admin);
        int rows = adminMapper.update(admin);
        if (rows > 0) {
            rbacMapper.deleteUserRoles(admin.getId());
            syncUserRole(admin.getId(), admin.getRole());
        }
        return rows;
    }

    @Override
    @Transactional
    public int delete(Integer id) {
        // 不能删除超级管理员
        Admin admin = adminMapper.selectById(id);
        if (admin != null && "superadmin".equals(admin.getRole())) {
            throw ApiException.forbidden("不能删除超级管理员");
        }
        int rows = adminMapper.delete(id);
        if (rows > 0) {
            rbacMapper.deleteUserRoles(id);
        }
        return rows;
    }

    private void syncUserRole(Integer accountId, String roleKey) {
        if (accountId == null || roleKey == null || roleKey.isBlank()) {
            return;
        }
        Integer roleId = rbacMapper.selectRoleIdByKey(roleKey);
        if (roleId != null) {
            rbacMapper.insertUserRole(accountId, roleId);
        }
    }

    @Override
    public Admin login(String email, String password) {
        String normalizedEmail = QqEmailAddress.normalize(email);
        Admin admin = adminMapper.selectByEmailHash(fieldCryptoService.blindIndex(normalizedEmail));
        
        if (admin == null) {
            return null;  // 用户不存在
        }
        
        if (!matchesPassword(password, admin.getPassword())) {
            return null;  // 密码错误
        }
        
        // 检查状态
        if (admin.getStatus() == 0) {
            throw ApiException.forbidden("账号已被禁用");
        }
        
        // 更新最后登录时间
        adminMapper.updateLastLoginTime(admin.getId());
        migrateLegacyPasswordIfNeeded(admin, password);
        decrypt(admin);
        
        // 不返回密码
        admin.setPassword(null);
        
        return admin;
    }
    
    @Override
    public int changePassword(Integer id, String oldPassword, String newPassword) {
        Admin admin = adminMapper.selectById(id);
        
        if (admin == null) {
            throw ApiException.notFound("用户不存在");
        }
        
        if (!matchesPassword(oldPassword, admin.getPassword())) {
            throw ApiException.badRequest("旧密码错误");
        }
        validateNewPassword(newPassword);
        
        // 更新密码
        admin.setPassword(passwordEncoder.encode(newPassword));
        return adminMapper.update(admin);
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    private void migrateLegacyPasswordIfNeeded(Admin admin, String rawPassword) {
        if (admin.getPassword() != null && !isBcrypt(admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(rawPassword));
            adminMapper.update(admin);
        }
    }

    private boolean isBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private void validateNewPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw ApiException.badRequest("密码长度必须为8到72位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));
        int categories = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSymbol ? 1 : 0);
        if (categories < 2 || password.chars().anyMatch(Character::isWhitespace)) {
            throw ApiException.badRequest("密码必须包含字母、数字或符号中的至少两类，且不能包含空白字符");
        }
    }

    private void validateAdminProfile(Admin admin, boolean requireId) {
        if (admin == null) {
            throw ApiException.badRequest("管理员信息不能为空");
        }
        if (requireId && admin.getId() == null) {
            throw ApiException.badRequest("管理员ID不能为空");
        }
        QqEmailAddress.normalize(admin.getEmail());
        if (admin.getRole() != null && !ALLOWED_ROLES.contains(admin.getRole())) {
            throw ApiException.badRequest("非法管理员角色");
        }
        if (admin.getStatus() != null && admin.getStatus() != 0 && admin.getStatus() != 1) {
            throw ApiException.badRequest("非法管理员状态");
        }
    }

    private void encrypt(Admin admin) {
        if (admin != null) {
            admin.setPhone(fieldCryptoService.encryptNullable(admin.getPhone()));
            admin.setEmail(fieldCryptoService.encryptNullable(admin.getEmail()));
        }
    }

    private Admin decrypt(Admin admin) {
        if (admin != null) {
            admin.setPhone(fieldCryptoService.decryptNullable(admin.getPhone()));
            admin.setEmail(fieldCryptoService.decryptNullable(admin.getEmail()));
        }
        return admin;
    }
}


