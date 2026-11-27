package com.omnibid.config;

import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapInitializer implements ApplicationRunner {

    static final String DEFAULT_ADMIN_EMAIL = "admin@omnibid.local";
    private static final String SEEDED_DEFAULT_PASSWORD = "Admin@123456";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin.initial-password:}")
    private String initialAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByEmail(DEFAULT_ADMIN_EMAIL).ifPresent(this::handleSeededAdmin);
    }

    private void handleSeededAdmin(User admin) {
        if (admin.getRole() != UserRole.ADMIN) {
            return;
        }
        boolean usingSeededPassword = passwordEncoder.matches(SEEDED_DEFAULT_PASSWORD, admin.getPasswordHash());
        if (!usingSeededPassword) {
            return;
        }

        if (StringUtils.hasText(initialAdminPassword)) {
            if (SEEDED_DEFAULT_PASSWORD.equals(initialAdminPassword)) {
                log.warn("SECURITY WARNING: app.security.admin.initial-password is set to the public default password. Change it immediately.");
                return;
            }
            admin.setPasswordHash(passwordEncoder.encode(initialAdminPassword));
            admin.increaseTokenVersion();
            userRepository.save(admin);
            log.info("Rotated seeded admin password from ADMIN_INITIAL_PASSWORD and invalidated previous sessions.");
            return;
        }

        log.warn("SECURITY WARNING: Default admin account '{}' is still using the seeded password. Set ADMIN_INITIAL_PASSWORD or change it via PATCH /api/v1/users/me/password immediately.",
                DEFAULT_ADMIN_EMAIL);
    }
}
