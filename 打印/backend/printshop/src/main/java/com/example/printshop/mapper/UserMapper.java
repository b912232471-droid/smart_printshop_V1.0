package com.example.printshop.mapper;

import com.example.printshop.entity.User;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface UserMapper {
    User selectById(Integer id);
    int update(User user);
    int delete(Integer id);
    List<User> selectAll();
}
