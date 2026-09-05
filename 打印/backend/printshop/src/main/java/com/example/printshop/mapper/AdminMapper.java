package com.example.printshop.mapper;

import com.example.printshop.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AdminMapper {
    Admin selectById(Integer id);
    Admin selectByUsername(String username);
    Admin selectByEmailHash(String emailHash);
    List<Admin> selectAll();
    int insert(Admin admin);
    int update(Admin admin);
    int delete(Integer id);
    int updateLastLoginTime(Integer id);
}


