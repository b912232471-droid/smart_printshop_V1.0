package com.example.printshop.bootstrap;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.Admin;
import com.example.printshop.mapper.AdminMapper;
import com.example.printshop.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminMapper adminMapper;
    private final AdminService adminService;
    private final boolean enabled;
    private final String username;
    private final String password;
    private final String realName;
    private final String phone;
    private final String email;

    public AdminBootstrapRunner(AdminMapper adminMapper,
                                AdminService adminService,
                                @Value("${printshop.bootstrap.admin.enabled:false}") boolean enabled,
                                @Value("${printshop.bootstrap.admin.username:}") String username,
                                @Value("${printshop.bootstrap.admin.password:}") String password,
                                @Value("${printshop.bootstrap.admin.real-name:}") String realName,
                                @Value("${printshop.bootstrap.admin.phone:}") String phone,
                                @Value("${printshop.bootstrap.admin.email:}") String email) {
        this.adminMapper = adminMapper;
        this.adminService = adminService;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.realName = realName;
        this.phone = phone;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        List<Admin> existingAdmins = adminMapper.selectAll();
        boolean hasQqEmailAdmin = existingAdmins != null && existingAdmins.stream()
                .anyMatch(admin -> admin.getEmailHash() != null && !admin.getEmailHash().isBlank());
        if (hasQqEmailAdmin) {
            log.info("superadmin bootstrap skipped because a QQ email administrator already exists");
            return;
        }
        if (existingAdmins != null && !existingAdmins.isEmpty()) {
            log.warn("existing administrator accounts have no usable QQ email; creating the configured recovery superadmin");
        }

        Admin admin = new Admin();
        admin.setUsername(requireNonBlank(email, "PRINTSHOP_BOOTSTRAP_ADMIN_EMAIL").trim());
        admin.setPassword(requireNonBlank(password, "PRINTSHOP_BOOTSTRAP_ADMIN_PASSWORD"));
        admin.setRealName(blankToNull(realName));
        admin.setPhone(blankToNull(phone));
        admin.setEmail(requireNonBlank(email, "PRINTSHOP_BOOTSTRAP_ADMIN_EMAIL").trim());
        admin.setRole("superadmin");
        admin.setStatus(1);

        try {
            adminService.register(admin);
        } catch (ApiException ex) {
            throw new IllegalStateException("failed to bootstrap superadmin: " + ex.getMessage(), ex);
        }
        log.warn("bootstrap superadmin account '{}' created; disable PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED after first login", admin.getUsername());
    }

    private String requireNonBlank(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envName + " is required when PRINTSHOP_BOOTSTRAP_ADMIN_ENABLED=true");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
