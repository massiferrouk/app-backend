package com.studup.backend.integration;

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
 * APP-98 : notification « nouveau match » sur vraie base PostgreSQL.
 *
 * Deux alternants aux villes inversées (Brest/Nancy vs Nancy/Brest) : la création
 * du second profil doit déclencher une notification NOUVEAU_MATCH pour les DEUX,
 * via l'événement AlternantProfileSavedEvent. Valide aussi la migration V23
 * (table match_notifications de déduplication).
 */
class MatchNotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldNotifyBothAlternantsWhenSecondProfileCreated() {
        // Villes exotiques et uniques : évite de matcher des profils laissés par
        // d'autres tests d'intégration (base Testcontainers partagée).
        String villeA = "Brest";
        String villeB = "Nancy";
        String emailA = "matchA_" + UUID.randomUUID() + "@studup.fr";
        String emailB = "matchB_" + UUID.randomUUID() + "@studup.fr";

        // A crée son profil en premier : aucun candidat encore → aucune notif
        String tokenA = registerConfirmedAlternantWithProfile(emailA, villeA, villeB);
        assertThat(countNouveauMatch(emailA)).isZero();

        // B crée son profil (villes inversées) → match avec A → notif pour les deux
        registerConfirmedAlternantWithProfile(emailB, villeB, villeA);

        assertThat(countNouveauMatch(emailA)).isEqualTo(1);
        assertThat(countNouveauMatch(emailB)).isEqualTo(1);

        // Déduplication : re-déclencher (A modifie son profil) ne renotifie pas.
        updateProfile(tokenA, villeA, villeB);
        assertThat(countNouveauMatch(emailA)).isEqualTo(1);
        assertThat(countNouveauMatch(emailB)).isEqualTo(1);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Integer countNouveauMatch(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications n JOIN users u ON u.id = n.user_id " +
                "WHERE u.email = ? AND n.type::text = 'NOUVEAU_MATCH'", Integer.class, email);
    }

    /** Enregistre + confirme un alternant, crée son profil, et retourne son JWT. */
    private String registerConfirmedAlternantWithProfile(String email, String villeA, String villeB) {
        var reg = restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, "Password123!", "Alice", "Martin", UserRole.ALTERNANT),
                Map.class);
        assertThat(reg.getStatusCode()).as("register " + email).isEqualTo(HttpStatus.CREATED);

        String token = jdbcTemplate.queryForObject(
                "SELECT t.token FROM email_confirmation_tokens t JOIN users u ON u.id=t.user_id " +
                "WHERE u.email=? ORDER BY t.created_at DESC LIMIT 1", String.class, email);
        var conf = restTemplate.postForEntity("/api/v1/auth/confirm?token=" + token, null, Map.class);
        assertThat(conf.getStatusCode()).as("confirm " + email).isEqualTo(HttpStatus.OK);

        String jwt = login(email);
        var prof = restTemplate.postForEntity("/api/v1/profile/alternant",
                profileBody(villeA, villeB, jwt), Map.class);
        assertThat(prof.getStatusCode()).as("create profile " + email + " -> " + prof.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return jwt;
    }

    private void updateProfile(String jwt, String villeA, String villeB) {
        restTemplate.exchange("/api/v1/profile/alternant",
                HttpMethod.PUT, profileBody(villeA, villeB, jwt), Map.class);
    }

    private HttpEntity<Map<String, Object>> profileBody(String villeA, String villeB, String jwt) {
        return new HttpEntity<>(Map.of(
                "villeA", villeA, "villeB", villeB,
                "ecole", "YNOV", "entreprise", "TechCorp",
                "dateDebut", "2026-09-07", "dateFin", "2027-07-12",
                "rythme", "SEMAINE_3_1"), authHeaders(jwt));
    }

    private String login(String email) {
        Map body = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "Password123!"), Map.class).getBody();
        return body.get("accessToken").toString();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
