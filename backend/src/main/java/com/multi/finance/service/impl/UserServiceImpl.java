package com.multi.finance.service.impl;

import com.multi.finance.dto.request.RegisterRequest;
import com.multi.finance.dto.request.UpdateUserRequest;
import com.multi.finance.dto.response.UserResponse;
import com.multi.finance.entity.User;
import com.multi.finance.entity.Worker;
import com.multi.finance.enums.UserRole;
import com.multi.finance.repository.UserRepository;
import com.multi.finance.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkerRepository workerRepository;

    public UserResponse createUser(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        User user = User.builder()
                .username(request.getUsername())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);

        // If WORKER role and a workerId provided, link the Worker entity
        if (request.getRole() == UserRole.WORKER && request.getWorkerId() != null) {
            Worker worker = workerRepository.findById(request.getWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found: " + request.getWorkerId()));
            worker.setUser(saved);
            workerRepository.save(worker);
        }

        return toResponse(saved);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse updateUser(Long id, UpdateUserRequest request, User adminUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getUsername().equals(request.getUsername()) &&
                userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }

        user.setFullName(request.getFullName());
        user.setUsername(request.getUsername());
        user.setRole(request.getRole());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getAdminCurrentPassword() == null || request.getAdminCurrentPassword().isBlank()
                    || !passwordEncoder.matches(request.getAdminCurrentPassword(), adminUser.getPasswordHash())) {
                throw new RuntimeException("Current admin password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            log.info("[User] Password changed for id={} username={} by admin={}", id, user.getUsername(), adminUser.getUsername());
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        user.setUpdatedAt(LocalDateTime.now());
        return toResponse(userRepository.save(user));
    }

    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("[User] Deactivated user id={} username={}", id, user.getUsername());
    }

    public void activateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("[User] Activated user id={} username={}", id, user.getUsername());
    }

    public void changePassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("Password cannot be blank");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("[User] Password changed for user id={} username={}", id, user.getUsername());
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .active(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
