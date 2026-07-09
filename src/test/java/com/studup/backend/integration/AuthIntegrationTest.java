package com.studup.backend.integration;

import com.studup.backend.model.dto.request.LoginRequest;
import com.studup.backend.model.dto.request.RefreshRequest;
import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flow E2E-01 : inscription → confirmation email → login → refresh → logout
 *
 * Ces tests utilisent une vraie base PostgreSQL (Testcontainers) et un vrai Redis.
 * Flyway applique toutes les migrations avant les tests.
 * APP-82 : le login exige un email confirmé — le helper register() passe par
 * le vrai endpoint /auth/confirm avec le token lu en base.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ─── inscription → HTTP 201 + email retourné ─────────────────────────────

    @Test
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest(
                "register_" + UUID.randomUUID() + "@studup.fr",
                "Password123!", "Alice", "Martin", UserRole.ALTERNANT);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("email");
    }

    // ─── email déjà utilisé → HTTP 409 ───────────────────────────────────────

    @Test
    void shouldRejectDuplicateEmail() {
        String email = "duplicate_" + UUID.randomUUID() + "@studup.fr";
        RegisterRequest request = new RegisterRequest(
                email, "Password123!", "Bob", "Dupont", UserRole.ALTERNANT);

        restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ─── login → HTTP 200 + accessToken + refreshToken ───────────────────────

    @Test
    void shouldLoginAndReturnTokens() {
        String email = register();

        LoginRequest loginRequest = new LoginRequest(email, "Password123!");
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login", loginRequest, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("accessToken", "refreshToken");
        assertThat(response.getBody().get("accessToken").toString()).isNotBlank();
    }

    // ─── refresh token → nouvelle paire de tokens ────────────────────────────

    @Test
    void shouldRefreshTokens() {
        String email = register();
        Map loginBody = login(email);

        String refreshToken = loginBody.get("refreshToken").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RefreshRequest> request = new HttpEntity<>(
                new RefreshRequest(refreshToken), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/refresh", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("accessToken", "refreshToken");
    }

    // ─── logout → access token blacklisté dans Redis → requête suivante rejetée

    @Test
    void shouldLogoutAndBlacklistToken() {
        String email = register();
        Map loginBody = login(email);

        String accessToken = loginBody.get("accessToken").toString();
        String refreshToken = loginBody.get("refreshToken").toString();

        // Logout avec accessToken dans le header ET refreshToken dans le body
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RefreshRequest> logoutRequest = new HttpEntity<>(
                new RefreshRequest(refreshToken), headers);

        ResponseEntity<Void> logoutResponse = restTemplate.postForEntity(
                "/api/v1/auth/logout", logoutRequest, Void.class);

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Après logout, le même accessToken doit être rejeté (blacklist Redis)
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        ResponseEntity<Map> protectedResponse = restTemplate.exchange(
                "/api/v1/notifications", HttpMethod.GET,
                new HttpEntity<>(authHeaders), Map.class);

        assertThat(protectedResponse.getStatusCode()).isIn(
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ─── confirmation email (APP-82) ─────────────────────────────────────────

    @Test
    void shouldRejectLoginWhenEmailNotConfirmed() {
        // Inscription SANS confirmation
        String email = "unconfirmed_" + UUID.randomUUID() + "@studup.fr";
        RegisterRequest request = new RegisterRequest(
                email, "Password123!", "Alice", "Martin", UserRole.ALTERNANT);
        restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, "Password123!"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("EMAIL_NOT_CONFIRMED");
    }

    @Test
    void shouldConfirmEmailThenLogin() {
        // register() confirme déjà via le vrai endpoint — si le login passe,
        // c'est que le flux complet inscription → confirm → login fonctionne
        String email = register();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, "Password123!"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReject400OnUnknownConfirmToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/confirm?token=token-inexistant", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void shouldReject400OnAlreadyUsedConfirmToken() {
        String email = register(); // consomme le token une première fois
        String token = confirmationTokenFor(email);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/confirm?token=" + token, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── validation — password trop court → HTTP 400 ─────────────────────────

    @Test
    void shouldReturn400WhenPasswordTooShort() {
        RegisterRequest request = new RegisterRequest(
                "short_" + UUID.randomUUID() + "@studup.fr",
                "abc", "Alice", "Martin", UserRole.ALTERNANT);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/register", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /// Inscrit un utilisateur ET confirme son email via le vrai endpoint
    /// (le login exige un compte vérifié depuis APP-82)
    private String register() {
        String email = "user_" + UUID.randomUUID() + "@studup.fr";
        RegisterRequest request = new RegisterRequest(
                email, "Password123!", "Alice", "Martin", UserRole.ALTERNANT);
        restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);

        String token = confirmationTokenFor(email);
        restTemplate.postForEntity("/api/v1/auth/confirm?token=" + token, null, Map.class);
        return email;
    }

    /// Récupère le token de confirmation en base (le SMTP est désactivé en test)
    private String confirmationTokenFor(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT t.token FROM email_confirmation_tokens t " +
                "JOIN users u ON u.id = t.user_id WHERE u.email = ? " +
                "ORDER BY t.created_at DESC LIMIT 1",
                String.class, email);
    }

    private Map login(String email) {
        LoginRequest request = new LoginRequest(email, "Password123!");
        return restTemplate.postForEntity("/api/v1/auth/login", request, Map.class).getBody();
    }
}
