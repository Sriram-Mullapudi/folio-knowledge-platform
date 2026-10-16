package com.ekp.bootstrap;

import com.ekp.user.Role;
import com.ekp.user.User;
import com.ekp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Bootstraps the first ADMIN account on startup. ADMIN is never grantable via
 * the API, so the initial admin must come from trusted configuration. If an
 * ADMIN already exists, or no seed credentials are configured, this is a no-op.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${ekp.admin.email:}") String adminEmail,
                       @Value("${ekp.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return; // already bootstrapped
        }
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.warn("No ADMIN exists and ekp.admin.email/password are not set; "
                    + "skipping admin seed. Set them to bootstrap an administrator.");
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            log.warn("Seed admin email {} already exists as a non-admin; not modifying it.", email);
            return;
        }
        User admin = new User(email, passwordEncoder.encode(adminPassword), Role.ADMIN);
        userRepository.save(admin);
        log.info("Seeded initial ADMIN account: {}", email);
    }
}
