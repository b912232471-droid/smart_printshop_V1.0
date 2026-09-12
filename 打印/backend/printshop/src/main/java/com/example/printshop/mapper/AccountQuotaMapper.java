package com.example.printshop.mapper;

import com.example.printshop.entity.AccountQuota;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountQuotaMapper {
    AccountQuota selectByAccountId(Integer accountId);

    int upsert(AccountQuota quota);
}
