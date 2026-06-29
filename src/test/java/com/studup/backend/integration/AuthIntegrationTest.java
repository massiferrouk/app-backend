package com.studup.backend.integration;

import com.studup.backend.model.dto.request.LoginRequest;
import com.studup.backend.model.dto.request.RefreshRequest;
import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flow E2E-01 : inscription → login → refresh token → logout
 *
 * Ces tests utilisent une vraie base PostgreSQL (Testcontainers) et un vrai Redis.
 * Flyway applique toutes les migrations V1→V21 avant les tests.
 * isVerified n'est pas requis pour le login — seul isActive=true l'est (valeur par défaut).
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

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

    private String register() {
        String email = "user_" + UUID.randomUUID() + "@studup.fr";
        RegisterRequest request = new RegisterRequest(
                email, "Password123!", "Alice", "Martin", UserRole.ALTERNANT);
        restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);
        return email;
    }

    private Map login(String email) {
        LoginRequest request = new LoginRequest(email, "Password123!");
        return restTemplate.postForEntity("/api/v1/auth/login", request, Map.class).getBody();
    }
}
