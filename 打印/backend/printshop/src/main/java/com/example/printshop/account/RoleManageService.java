package com.example.printshop.account;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.RbacMenu;
import com.example.printshop.entity.SysRole;
import com.example.printshop.mapper.RbacMapper;
import com.example.printshop.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理端角色管理：角色 CRUD 与角色-菜单授权。
 * 内置角色可编辑授权但不可删除；superadmin 内置角色不可停用。
 */
@Service
public class RoleManageService {
    private static final Set<String> DATA_SCOPES = Set.of("ALL", "STORE", "SELF");
    private static final String ROLE_KEY_PATTERN = "^[a-z][a-z0-9_]{1,31}$";

    private final SysRoleMapper sysRoleMapper;
    private final RbacMapper rbacMapper;

    public RoleManageService(SysRoleMapper sysRoleMapper, RbacMapper rbacMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.rbacMapper = rbacMapper;
    }

    public List<SysRole> list() {
        return sysRoleMapper.selectAllWithUserCount();
    }

    public List<RbacMenu> menuTree() {
        return rbacMapper.selectAllMenus();
    }

    public List<Integer> roleMenuIds(Integer roleId) {
        requireRole(roleId);
        return sysRoleMapper.selectMenuIdsByRoleId(roleId);
    }

    public SysRole get(Integer roleId) {
        return requireRole(roleId);
    }

    @Transactional
    public SysRole create(SysRole role, List<Integer> menuIds) {
        validateProfile(role, false);
        if (sysRoleMapper.selectByKey(role.getRoleKey()) != null) {
            throw ApiException.badRequest("角色标识已存在: " + role.getRoleKey());
        }
        validateMenuIds(menuIds);
        sysRoleMapper.insert(role);
        replaceRoleMenus(role.getId(), menuIds);
        return sysRoleMapper.selectById(role.getId());
    }

    @Transactional
    public SysRole update(Integer roleId, SysRole payload, List<Integer> menuIds) {
        SysRole existing = requireRole(roleId);
        payload.setId(roleId);
        payload.setRoleKey(existing.getRoleKey());
        validateProfile(payload, true);
        if ("superadmin".equals(existing.getRoleKey()) && payload.getStatus() != null && payload.getStatus() == 0) {
            throw ApiException.badRequest("superadmin 内置角色不允许停用");
        }
        sysRoleMapper.update(payload);
        if (menuIds != null) {
            validateMenuIds(menuIds);
            replaceRoleMenus(roleId, menuIds);
        }
        return sysRoleMapper.selectById(roleId);
    }

    @Transactional
    public void delete(Integer roleId) {
        SysRole existing = requireRole(roleId);
        if (existing.getBuiltin() != null && existing.getBuiltin() == 1) {
            throw ApiException.forbidden("内置角色不允许删除");
        }
        int users = sysRoleMapper.countUsers(roleId);
        if (users > 0) {
            throw ApiException.badRequest("仍有 " + users + " 个账户使用该角色，请先调整账户角色后再删除");
        }
        sysRoleMapper.delete(roleId);
        sysRoleMapper.deleteRoleMenus(roleId);
    }

    private void replaceRoleMenus(Integer roleId, List<Integer> menuIds) {
        sysRoleMapper.deleteRoleMenus(roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        sysRoleMapper.insertRoleMenus(roleId, new LinkedHashSet<>(menuIds).stream().toList());
    }

    private void validateMenuIds(List<Integer> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        Set<Integer> distinct = new HashSet<>(menuIds);
        if (sysRoleMapper.countMenusByIds(new LinkedHashSet<>(distinct).stream().toList()) != distinct.size()) {
            throw ApiException.badRequest("授权菜单中包含不存在的菜单项");
        }
    }

    private void validateProfile(SysRole role, boolean requireId) {
        if (role == null || role.getRoleKey() == null || !role.getRoleKey().matches(ROLE_KEY_PATTERN)) {
            throw ApiException.badRequest("角色标识须为 2-32 位小写字母开头的字母/数字/下划线");
        }
        if (role.getRoleName() == null || role.getRoleName().isBlank() || role.getRoleName().length() > 32) {
            throw ApiException.badRequest("角色名称不能为空且不超过 32 个字符");
        }
        if (role.getDataScope() != null && !DATA_SCOPES.contains(role.getDataScope())) {
            throw ApiException.badRequest("数据范围须为 ALL/STORE/SELF");
        }
        if (role.getStatus() != null && role.getStatus() != 0 && role.getStatus() != 1) {
            throw ApiException.badRequest("非法角色状态");
        }
        if (role.getRoleSort() == null) {
            role.setRoleSort(0);
        }
        if (role.getDataScope() == null) {
            role.setDataScope("SELF");
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        if (requireId && role.getId() == null) {
            throw ApiException.badRequest("角色ID不能为空");
        }
        if (role.getRemark() != null && role.getRemark().length() > 255) {
            throw ApiException.badRequest("备注不能超过 255 个字符");
        }
    }

    private SysRole requireRole(Integer roleId) {
        if (roleId == null) {
            throw ApiException.badRequest("角色ID不能为空");
        }
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw ApiException.notFound("角色不存在");
        }
        return role;
    }
}
