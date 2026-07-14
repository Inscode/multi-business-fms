package com.multi.finance.controller;

import com.multi.finance.dto.request.CopilotAuthRequest;
import com.multi.finance.dto.response.CopilotAuthResponse;
import com.multi.finance.entity.User;
import com.multi.finance.service.impl.CopilotAuthService;
import com.multi.finance.util.Jwtutil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/copilot/auth")
@RequiredArgsConstructor
public class CopilotAuthController {

    private final CopilotAuthService copilotAuthService;
    private final Jwtutil jwtutil;

    /** POST /api/copilot/auth/login — no Bearer token required */
    @PostMapping("/login")
    public ResponseEntity<CopilotAuthResponse> login(@Valid @RequestBody CopilotAuthRequest request) {
        return ResponseEntity.ok(copilotAuthService.login(request));
    }

    /**
     * POST /api/copilot/auth/refresh — requires a still-valid Bearer token.
     * Issues a fresh 24-hour token. Call this before the current token expires.
     */
    @PostMapping("/refresh")
    public ResponseEntity<CopilotAuthResponse> refresh(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(copilotAuthService.refresh(user));
    }

    /**
     * GET /api/copilot/auth/check — requires a still-valid Bearer token.
     * Returns validity status and seconds remaining. Use to decide when to refresh.
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> check(
            @AuthenticationPrincipal User user,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Date expiry = jwtutil.getExpirationDate(token);
        long secondsRemaining = (expiry.getTime() - System.currentTimeMillis()) / 1000;
        log.info("[CopilotAuth] Check: user={}, secondsRemaining={}", user.getUsername(), secondsRemaining);
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "username", user.getUsername(),
                "role", user.getRole().name(),
                "expiresAt", DateTimeFormatter.ISO_INSTANT.format(expiry.toInstant()),
                "secondsRemaining", secondsRemaining
        ));
    }
}
