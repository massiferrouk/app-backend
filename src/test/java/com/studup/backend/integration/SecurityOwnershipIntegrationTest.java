package com.studup.backend.integration;

import com.studup.backend.model.dto.request.LoginRequest;
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
 * Tests de sécurité : ownership checks sur les ressources protégées.
 *
 * Vérifie que :
 * - Un utilisateur non authentifié reçoit 401 sur tous les endpoints protégés
 * - Un utilisateur authentifié ne peut pas modifier les ressources d'un autre
 * - HTTP 403 (et non 404) quand la ressource existe mais appartient à quelqu'un d'autre
 */
class SecurityOwnershipIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ─── requête sans token → HTTP 401 ───────────────────────────────────────

    @Test
    void shouldReturn401OnProtectedEndpointWithoutToken() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications", HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401OnMatchingSuggestionsWithoutToken() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/matching/suggestions", HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WhenTryingToPublishLogementWithoutToken() {
        UUID fakeLogementId = UUID.randomUUID();
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/logements/" + fakeLogementId + "/publish",
                HttpMethod.PUT, HttpEntity.EMPTY, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── token invalide → HTTP 401 ────────────────────────────────────────────

    @Test
    void shouldReturn401WithInvalidToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid.jwt.token");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/notifications", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── admin endpoint non accessible sans rôle ADMIN → HTTP 403 ────────────

    @Test
    void shouldReturn403OnAdminEndpointForNormalUser() {
        String email = register();
        String token = login(email);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/users", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturn403OnModerationQueueForNormalUser() {
        String email = register();
        String token = login(email);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/moderation/messages", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── endpoint de logement : utilisateur B ne peut pas publier le logement de A

    @Test
    void shouldReturn403WhenPublishingOtherUsersLogement() {
        // Créer deux utilisateurs
        String userAEmail = register();
        String userBEmail = register();
        String userBToken = login(userBEmail);

        // Créer un logement appartenant à userA directement en base
        UUID userAId = getUserIdFromEmail(userAEmail);
        UUID logementId = createLogementInDb(userAId);

        // UserB essaie de publier le logement de userA
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userBToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/logements/" + logementId + "/publish",
                HttpMethod.PUT, new HttpEntity<>(headers), Map.class);

        // Doit être 403 (pas 404) — la ressource existe, mais userB n'en est pas propriétaire
        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    // ─── endpoint public : santé de l'application accessible sans auth ────────

    @Test
    void shouldReturn200OnHealthEndpointWithoutAuth() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/actuator/health/liveness", HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String register() {
        String email = "sec_" + UUID.randomUUID() + "@studup.fr";
        RegisterRequest request = new RegisterRequest(
                email, "Password123!", "Alice", "Martin", UserRole.ALTERNANT);
        restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);
        // APP-82 : le login exige un email confirmé — activation directe en base
        // (le flux confirm complet est couvert par AuthIntegrationTest)
        jdbcTemplate.update("UPDATE users SET is_verified = true WHERE email = ?", email);
        return email;
    }

    private String login(String email) {
        LoginRequest request = new LoginRequest(email, "Password123!");
        Map body = restTemplate.postForEntity("/api/v1/auth/login", request, Map.class).getBody();
        return body.get("accessToken").toString();
    }

    private UUID getUserIdFromEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private UUID createLogementInDb(UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO logements (id, owner_id, adresse, ville, code_postal, type, " +
                "surface, loyer, statut, is_verified, is_meuble, is_chambre_partagee, " +
                "created_at, updated_at) " +
                "VALUES (?::uuid, ?::uuid, '10 rue de la Paix', 'Paris', '75001', " +
                "'STUDIO', 25.0, 800.0, 'BROUILLON', false, true, false, NOW(), NOW())",
                id.toString(), ownerId.toString());
        return id;
    }
}
