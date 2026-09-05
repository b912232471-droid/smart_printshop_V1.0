package com.example.printshop.service;

import java.util.Map;

public interface RbacService {
    Map<String, Object> getAccountPermissions(Integer accountId);
}
