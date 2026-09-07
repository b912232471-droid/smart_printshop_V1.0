package com.example.printshop.mapper;

import com.example.printshop.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AccountMapper {
    Account selectById(Integer id);
    Account selectWithPermsById(Integer id);
    Account selectByUsername(String username);
    Account selectByEmailHash(String emailHash);
    List<Account> selectMissingEmailHashes();
    int insertUser(Account account);
    int updatePassword(@Param("id") Integer id, @Param("passwordHash") String passwordHash);
    int updateLastLoginTime(Integer id);
    int updateEmailIdentity(@Param("id") Integer id, @Param("email") String email, @Param("emailHash") String emailHash);
    List<Account> selectAccountPage(@Param("keyword") String keyword,
                                    @Param("accountType") String accountType,
                                    @Param("role") String role,
                                    @Param("status") Integer status,
                                    @Param("offset") int offset,
                                    @Param("pageSize") int pageSize);
    long countAccountPage(@Param("keyword") String keyword,
                          @Param("accountType") String accountType,
                          @Param("role") String role,
                          @Param("status") Integer status);
    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);
    int updateRoleById(@Param("id") Integer id, @Param("role") String role);
}
