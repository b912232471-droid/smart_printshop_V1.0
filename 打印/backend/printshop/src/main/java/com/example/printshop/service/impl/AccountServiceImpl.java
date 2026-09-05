package com.example.printshop.service.impl;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.Account;
import com.example.printshop.mapper.AccountMapper;
import com.example.printshop.mapper.RbacMapper;
import com.example.printshop.security.FieldCryptoService;
import com.example.printshop.security.QqEmailAddress;
import com.example.printshop.service.AccountService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {
    private final AccountMapper accountMapper;
    private final RbacMapper rbacMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final FieldCryptoService cryptoService;

    public AccountServiceImpl(AccountMapper accountMapper, RbacMapper rbacMapper, BCryptPasswordEncoder passwordEncoder, FieldCryptoService cryptoService) {
        this.accountMapper = accountMapper;
        this.rbacMapper = rbacMapper;
        this.passwordEncoder = passwordEncoder;
        this.cryptoService = cryptoService;
    }

    @Override
    @Transactional
    public Account registerUser(String email, String password, String displayName) {
        String normalized = QqEmailAddress.normalize(email);
        validateNewPassword(password);
        if (emailExists(normalized)) {
            throw ApiException.badRequest("该QQ邮箱已注册");
        }
        String normalizedDisplayName = displayName == null || displayName.isBlank() ? maskEmail(normalized) : displayName.trim();
        if (normalizedDisplayName.length() > 64) {
            throw ApiException.badRequest("昵称不能超过64个字符");
        }
        Account account = new Account();
        account.setUsername(normalized);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setAccountType("user");
        account.setRole("user");
        account.setStatus(1);
        account.setDisplayName(normalizedDisplayName);
        account.setEmail(cryptoService.encryptNullable(normalized));
        account.setEmailHash(cryptoService.blindIndex(normalized));
        accountMapper.insertUser(account);
        assignRole(account.getId(), account.getRole());
        return accountMapper.selectById(account.getId());
    }

    @Override
    public Account authenticate(String email, String password, String requiredType) {
        if (email == null || password == null) {
            return null;
        }
        Account account = findByQqEmail(email);
        if (account == null || (requiredType != null && !requiredType.equals(account.getAccountType()))) {
            return null;
        }
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw ApiException.forbidden("账号已被禁用");
        }
        if (!isBcrypt(account.getPasswordHash()) || !passwordEncoder.matches(password, account.getPasswordHash())) {
            return null;
        }
        accountMapper.updateLastLoginTime(account.getId());
        account.setPasswordHash(null);
        return account;
    }

    @Override
    public Account findByQqEmail(String email) {
        String normalized = QqEmailAddress.normalize(email);
        return accountMapper.selectByEmailHash(cryptoService.blindIndex(normalized));
    }

    @Override
    public boolean emailExists(String email) {
        return findByQqEmail(email) != null;
    }

    @Override
    @Transactional
    public int bindQqEmail(Integer id, String email, String currentPassword) {
        Account account = requireActive(id);
        if (!isBcrypt(account.getPasswordHash()) || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw ApiException.badRequest("当前密码错误");
        }
        String normalized = QqEmailAddress.normalize(email);
        Account existing = findByQqEmail(normalized);
        if (existing != null && !existing.getId().equals(id)) {
            throw ApiException.badRequest("该QQ邮箱已被其他账号使用");
        }
        return accountMapper.updateEmailIdentity(id, cryptoService.encryptNullable(normalized), cryptoService.blindIndex(normalized));
    }

    @Override
    public Account requireActive(Integer id) {
        Account account = accountMapper.selectWithPermsById(id);
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            throw ApiException.unauthorized("account is unavailable");
        }
        return account;
    }

    private void assignRole(Integer accountId, String roleKey) {
        if (accountId == null || roleKey == null || roleKey.isBlank()) {
            return;
        }
        Integer roleId = rbacMapper.selectRoleIdByKey(roleKey);
        if (roleId != null) {
            rbacMapper.insertUserRole(accountId, roleId);
        }
    }

    @Override
    public int changePassword(Integer id, String oldPassword, String newPassword) {
        Account account = requireActive(id);
        if (!isBcrypt(account.getPasswordHash()) || !passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            throw ApiException.badRequest("原密码错误");
        }
        validateNewPassword(newPassword);
        return accountMapper.updatePassword(id, passwordEncoder.encode(newPassword));
    }

    @Override
    public int resetPassword(Integer id, String newPassword) {
        requireActive(id);
        validateNewPassword(newPassword);
        return accountMapper.updatePassword(id, passwordEncoder.encode(newPassword));
    }

    private void validateNewPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72 || password.chars().anyMatch(Character::isWhitespace)) {
            throw ApiException.badRequest("密码须为8到72位且不能包含空白字符");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if ((hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSymbol ? 1 : 0) < 2) {
            throw ApiException.badRequest("密码须包含字母、数字或符号中的至少两类");
        }
    }

    private boolean isBcrypt(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return local.length() <= 3 ? local.charAt(0) + "***" : local.substring(0, 3) + "***";
    }
}
