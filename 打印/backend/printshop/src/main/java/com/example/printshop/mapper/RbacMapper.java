package com.example.printshop.mapper;

import com.example.printshop.entity.RbacMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RbacMapper {
    Integer selectRoleIdByKey(String roleKey);

    int insertUserRole(@Param("accountId") Integer accountId, @Param("roleId") Integer roleId);

    int deleteUserRoles(Integer accountId);

    List<String> selectRoleKeys(Integer accountId);

    List<String> selectPermissions(Integer accountId);

    List<String> selectAllPermissions();

    List<RbacMenu> selectMenus(Integer accountId);

    List<RbacMenu> selectAllMenus();
}
