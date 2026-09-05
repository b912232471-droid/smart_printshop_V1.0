package com.example.printshop.service.impl;

import com.example.printshop.entity.RbacMenu;
import com.example.printshop.mapper.RbacMapper;
import com.example.printshop.service.RbacService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RbacServiceImpl implements RbacService {
    private final RbacMapper rbacMapper;

    public RbacServiceImpl(RbacMapper rbacMapper) {
        this.rbacMapper = rbacMapper;
    }

    @Override
    public Map<String, Object> getAccountPermissions(Integer accountId) {
        List<String> roles = rbacMapper.selectRoleKeys(accountId);
        boolean superAdmin = roles.contains("superadmin");
        List<String> perms = superAdmin ? rbacMapper.selectAllPermissions() : rbacMapper.selectPermissions(accountId);
        List<RbacMenu> menus = superAdmin ? rbacMapper.selectAllMenus() : rbacMapper.selectMenus(accountId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roles", roles);
        data.put("perms", perms);
        data.put("menus", menus);
        return data;
    }
}
