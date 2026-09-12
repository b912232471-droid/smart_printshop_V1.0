package com.example.printshop.mapper;

import com.example.printshop.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMapper {
    List<SysRole> selectAll();

    List<SysRole> selectAllWithUserCount();

    SysRole selectById(Integer id);

    SysRole selectByKey(String roleKey);

    int insert(SysRole role);

    int update(SysRole role);

    int delete(Integer id);

    int countUsers(Integer roleId);

    List<Integer> selectMenuIdsByRoleId(Integer roleId);

    int deleteRoleMenus(Integer roleId);

    int insertRoleMenus(@Param("roleId") Integer roleId, @Param("menuIds") List<Integer> menuIds);

    int countMenusByIds(@Param("menuIds") List<Integer> menuIds);
}
