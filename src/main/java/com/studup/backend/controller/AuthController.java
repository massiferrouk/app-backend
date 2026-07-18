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
import org.springframework.http.MediaType;
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
    // Utilisé par l'app mobile (réponse JSON)
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(@RequestParam String token) {
        emailConfirmationService.confirmToken(token);
        return ResponseEntity.ok(Map.of("message", "Email confirmé, tu peux te connecter"));
    }

    // GET /api/v1/auth/confirm?token=... → page HTML (APP-116)
    // C'est CE lien qui est mis dans l'email : un clic = une requête GET.
    // Renvoie une page de succès (ou d'erreur) lisible directement dans le navigateur.
    @GetMapping(value = "/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmViaLink(@RequestParam String token) {
        try {
            emailConfirmationService.confirmToken(token);
            return ResponseEntity.ok(htmlPage(
                    "#27AE60", "Email confirmé ✓",
                    "Ton compte StudUp est activé. Tu peux maintenant te connecter dans l'application."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(htmlPage(
                    "#E74C3C", "Lien invalide",
                    e.getMessage()));
        }
    }

    // Petite page HTML autonome (pas de template : réponse directe au clic email)
    private String htmlPage(String couleur, String titre, String message) {
        return """
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>StudUp</title></head>
                <body style="font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:0;">
                <div style="max-width:480px;margin:64px auto;background:#fff;border-radius:8px;
                            padding:40px;text-align:center;">
                  <h1 style="color:%s;font-size:22px;">%s</h1>
                  <p style="color:#1A1A1A;font-size:15px;line-height:1.5;">%s</p>
                </div></body></html>
                """.formatted(couleur, titre, message);
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
