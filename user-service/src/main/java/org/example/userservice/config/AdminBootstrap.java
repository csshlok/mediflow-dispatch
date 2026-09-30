package org.example.userservice.config;

import org.example.userservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Registration requires an admin token, so the very first admin is created from deployment config
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final String email;
    private final String password;

    public AdminBootstrap(UserService userService,
                          @Value("${bootstrap.admin.email:}") String email,
                          @Value("${bootstrap.admin.password:}") String password) {
        this.userService = userService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.warn("BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD not set; no admin will be created if none exists");
            return;
        }

        if (userService.createAdminIfNoneExists("Bootstrap Admin", email, password)) {
            log.info("Created bootstrap admin account {}", email);
        }
    }
}
