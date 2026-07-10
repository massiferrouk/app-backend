package com.studup.backend.controller;

import com.studup.backend.model.dto.request.LoginRequest;
import com.studup.backend.model.dto.request.RefreshRequest;
import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.dto.response.AuthResponse;
import com.studup.backend.model.dto.response.UserResponse;
import com.studup.backend.service.AuthService;
import com.studup.backend.service.EmailConfirmationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailConfirmationService emailConfirmationService;

    public AuthController(AuthService authService,
                          EmailConfirmationService emailConfirmationService) {
        this.authService = authService;
        this.emailConfirmationService = emailConfirmationService;
    }

    // POST /api/v1/auth/register → 201 + UserResponse
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // POST /api/v1/auth/confirm?token=... → 200 (APP-82)
    // Valide le token reçu par email et active le compte
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(@RequestParam String token) {
        emailConfirmationService.confirmToken(token);
        return ResponseEntity.ok(Map.of("message", "Email confirmé, tu peux te connecter"));
    }

    // POST /api/v1/auth/login → 200 + AuthResponse (accessToken + refreshToken)
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // POST /api/v1/auth/refresh → 200 + nouvelle paire de tokens
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    // POST /api/v1/auth/logout → 204 No Content
    // L'access token est extrait du header Authorization pour être blacklisté dans Redis
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody RefreshRequest request) {
        String accessToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;
        authService.logout(accessToken, request);
        return ResponseEntity.noContent().build();
    }
}
