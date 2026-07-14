package com.multi.finance.service.impl;

import com.multi.finance.dto.request.CopilotAuthRequest;
import com.multi.finance.dto.response.CopilotAuthResponse;
import com.multi.finance.entity.User;
import com.multi.finance.util.Jwtutil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotAuthService {

    private final AuthenticationManager authenticationManager;
    private final Jwtutil jwtutil;

    public CopilotAuthResponse login(CopilotAuthRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        User user = (User) auth.getPrincipal();
        String token = jwtutil.generateToken(user);
        log.info("[CopilotAuth] Login successful: user={}, role={}", user.getUsername(), user.getRole());
        return buildResponse(token, user);
    }

    public CopilotAuthResponse refresh(User user) {
        String token = jwtutil.generateToken(user);
        log.info("[CopilotAuth] Token refreshed: user={}", user.getUsername());
        return buildResponse(token, user);
    }

    private CopilotAuthResponse buildResponse(String token, User user) {
        Date expiry = jwtutil.getExpirationDate(token);
        long secondsRemaining = (expiry.getTime() - System.currentTimeMillis()) / 1000;
        String expiresAt = DateTimeFormatter.ISO_INSTANT.format(expiry.toInstant());
        return CopilotAuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(secondsRemaining)
                .expiresAt(expiresAt)
                .build();
    }
}
