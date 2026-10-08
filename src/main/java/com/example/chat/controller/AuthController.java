package com.example.chat.controller;

import com.example.chat.dto.AuthDTO;
import com.example.chat.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Auth controller — stateless request/response (per README).
 * POST /api/auth/signup  →  create account, returns JWT
 * POST /api/auth/login   →  verify credentials, returns JWT
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthDTO.AuthResponse> signup(
            @Valid @RequestBody AuthDTO.SignupRequest req) {
        return ResponseEntity.ok(authService.signup(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDTO.AuthResponse> login(
            @Valid @RequestBody AuthDTO.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
