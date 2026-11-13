package com.fashtry.backend.config;

import com.fashtry.backend.bean.db.AdminUser;
import com.fashtry.backend.mapper.AdminUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * Creates the first admin account when {@code admin_users} is empty.
 * Credentials come from {@code app.admin.default-username} / {@code app.admin.default-password}.
 * If no password is configured, a random one is generated and logged once.
 */
@Component
@Order(100)
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final int GENERATED_PASSWORD_LENGTH = 16;

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminUserInitializer(AdminUserMapper adminUserMapper,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.default-username:admin}") String username,
                                @Value("${app.admin.default-password:}") String password) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = adminUserMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        boolean generated = !StringUtils.hasText(password);
        String initialPassword = generated ? generatePassword() : password;
        AdminUser u = new AdminUser();
        u.setUsername(StringUtils.hasText(username) ? username : "admin");
        u.setPasswordHash(passwordEncoder.encode(initialPassword));
        u.setStatus(1);
        adminUserMapper.insert(u);
        if (generated) {
            log.warn("Created admin account '{}' with generated password: {} — change it after first login",
                    u.getUsername(), initialPassword);
        } else {
            log.info("Created admin account '{}' from app.admin.default-password", u.getUsername());
        }
    }

    private String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return sb.toString();
    }
}
