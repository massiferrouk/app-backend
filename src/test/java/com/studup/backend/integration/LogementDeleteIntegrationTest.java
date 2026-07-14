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
 * APP-96 (recette) : suppression d'un logement AVEC photo sur vraie base.
 * Les suppressions sans photo passaient, celles avec photo échouaient (500).
 */
class LogementDeleteIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldDeleteLogementThatHasAPhotoRow() {
        String token = registerConfirmedProprietaire(
                "del_" + UUID.randomUUID() + "@studup.fr");

        UUID logementId = createLogement(token);

        // Simule une photo déjà en base (sans passer par l'upload MinIO)
        jdbcTemplate.update(
                "INSERT INTO photos_logements (id, logement_id, file_key, ordre, created_at) " +
                "VALUES (?, ?, ?, 0, NOW())",
                UUID.randomUUID(), logementId, "logements/" + UUID.randomUUID() + ".jpg");

        ResponseEntity<Void> resp = restTemplate.exchange(
                "/api/v1/logements/" + logementId,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer restant = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM logements WHERE id=?", Integer.class, logementId);
        assertThat(restant).isZero();
    }

    private String registerConfirmedProprietaire(String email) {
        restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, "Password123!", "Rabah", "Ammour", UserRole.PROPRIETAIRE),
                Map.class);
        String token = jdbcTemplate.queryForObject(
                "SELECT t.token FROM email_confirmation_tokens t JOIN users u ON u.id=t.user_id " +
                "WHERE u.email=? ORDER BY t.created_at DESC LIMIT 1", String.class, email);
        restTemplate.postForEntity("/api/v1/auth/confirm?token=" + token, null, Map.class);
        Map body = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "Password123!"), Map.class).getBody();
        return body.get("accessToken").toString();
    }

    private UUID createLogement(String token) {
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of(
                "adresse", "1 rue Test", "ville", "Marseille", "codePostal", "13001",
                "type", "STUDIO", "surface", 25, "nbPieces", 1,
                "loyer", 600, "charges", 0, "isMeuble", true), authHeaders(token));
        Map body = restTemplate.postForEntity("/api/v1/logements", req, Map.class).getBody();
        return UUID.fromString(body.get("id").toString());
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
