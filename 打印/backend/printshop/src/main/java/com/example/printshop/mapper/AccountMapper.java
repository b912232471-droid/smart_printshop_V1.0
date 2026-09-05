package com.example.printshop.mapper;

import com.example.printshop.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AccountMapper {
    Account selectById(Integer id);
    Account selectByUsername(String username);
    Account selectByEmailHash(String emailHash);
    List<Account> selectMissingEmailHashes();
    int insertUser(Account account);
    int updatePassword(@Param("id") Integer id, @Param("passwordHash") String passwordHash);
    int updateLastLoginTime(Integer id);
    int updateEmailIdentity(@Param("id") Integer id, @Param("email") String email, @Param("emailHash") String emailHash);
}
