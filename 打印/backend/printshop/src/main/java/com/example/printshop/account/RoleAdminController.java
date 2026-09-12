package com.example.printshop.account;

import com.example.printshop.common.ApiResponse;
import com.example.printshop.entity.SysRole;
import com.example.printshop.security.AuthContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端角色管理接口：角色 CRUD 与角色-菜单授权（superadmin 专属授权面）。
 */
@RestController
@RequestMapping("/api/role")
public class RoleAdminController {

    private final RoleManageService roleManageService;

    public RoleAdminController(RoleManageService roleManageService) {
        this.roleManageService = roleManageService;
    }

    @GetMapping("/list")
    public ApiResponse<List<SysRole>> list() {
        AuthContext.requirePermission("print:role:list");
        return ApiResponse.ok(roleManageService.list());
    }

    @GetMapping("/menu-tree")
    public ApiResponse<List<?>> menuTree() {
        AuthContext.requirePermission("print:role:list");
        return ApiResponse.ok(roleManageService.menuTree());
    }

    @GetMapping("/{id}/menus")
    public ApiResponse<Map<String, Object>> roleMenus(@PathVariable Integer id) {
        AuthContext.requirePermission("print:role:list");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", roleManageService.get(id));
        data.put("menuIds", roleManageService.roleMenuIds(id));
        return ApiResponse.ok(data);
    }

    @PostMapping("/")
    public ApiResponse<SysRole> create(@RequestBody Map<String, Object> body) {
        AuthContext.requirePermission("print:role:add");
        SysRole role = new SysRole();
        role.setRoleKey(str(body, "roleKey"));
        role.setRoleName(str(body, "roleName"));
        role.setRoleSort(intValue(body, "roleSort"));
        role.setDataScope(str(body, "dataScope"));
        role.setStatus(intValue(body, "status"));
        role.setRemark(str(body, "remark"));
        return ApiResponse.ok(roleManageService.create(role, menuIds(body)));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysRole> update(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        AuthContext.requirePermission("print:role:update");
        SysRole role = new SysRole();
        role.setRoleName(str(body, "roleName"));
        role.setRoleSort(intValue(body, "roleSort"));
        role.setDataScope(str(body, "dataScope"));
        role.setStatus(intValue(body, "status"));
        role.setRemark(str(body, "remark"));
        return ApiResponse.ok(roleManageService.update(id, role, menuIds(body)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        AuthContext.requirePermission("print:role:delete");
        roleManageService.delete(id);
        return ApiResponse.ok(null);
    }

    private List<Integer> menuIds(Map<String, Object> body) {
        if (body == null || !(body.get("menuIds") instanceof List<?> raw)) {
            return null;
        }
        return raw.stream()
                .map(item -> {
                    try {
                        return Integer.valueOf(String.valueOf(item));
                    } catch (NumberFormatException exception) {
                        return null;
                    }
                })
                .filter(item -> item != null)
                .toList();
    }

    private String str(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
