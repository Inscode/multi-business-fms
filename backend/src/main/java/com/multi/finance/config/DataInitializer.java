package com.multi.finance.config;

import com.multi.finance.entity.User;
import com.multi.finance.enums.UserRole;
import com.multi.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.password:password123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        createUserIfNotExists(
                "admin",
                "Admin User",
                UserRole.ADMIN,
                adminPassword
        );
    }

    private void createUserIfNotExists(
            String username,
            String fullName,
            UserRole role,
            String password) {

        if (userRepository.findByUsername(username).isPresent()) {
            log.info("[Init] User '{}' already exists, skipping.", username);
            return;
        }

        userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(role)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        log.info("[Init] User '{}' created with role {}.", username, role);
    }
}
