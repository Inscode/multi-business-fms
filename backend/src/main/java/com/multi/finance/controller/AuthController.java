package com.multi.finance.controller;


import com.multi.finance.dto.request.LoginRequest;
import com.multi.finance.dto.request.RegisterRequest;
import com.multi.finance.dto.response.AuthResponse;
import com.multi.finance.service.impl.AuthServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthServiceImpl authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
            ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
            ) {
        return ResponseEntity.ok(authService.register(request));
    }

    @GetMapping("/hi")
    public ResponseEntity<String> check() {
        return ResponseEntity.ok("endpoint working");
    }
}
