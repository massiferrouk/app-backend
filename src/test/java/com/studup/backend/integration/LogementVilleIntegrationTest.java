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
 * APP-91 : association d'un logement à une ville, sur VRAIE base PostgreSQL.
 *
 * Ce test touche le type ENUM natif ville_associee — un test unitaire mocké
 * ne l'aurait pas attrapé (le bug était une comparaison SQL impossible entre
 * l'enum natif et une chaîne). Découvert en recette manuelle.
 */
class LogementVilleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAssociateLogementToVilleThenRejectDuplicate() {
        // Alternant Paris (école) / Lyon (entreprise)
        String email = "villealt_" + UUID.randomUUID() + "@studup.fr";
        String token = registerConfirmedAlternant(email, "Paris", "Lyon");

        // Logement à Lyon = villeB
        UUID logementId = createLogement(token, "Lyon", "69001");

        // 1. Association à VILLE_B (Lyon) → 200 (le chemin qui plantait en 500)
        ResponseEntity<Map> ok = patchVille(token, logementId, "VILLE_B");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("villeAssociee")).isEqualTo("VILLE_B");

        // 2. Un 2e logement à Lyon, association VILLE_B déjà prise → 409
        UUID logement2 = createLogement(token, "Lyon", "69002");
        ResponseEntity<Map> conflict = patchVille(token, logement2, "VILLE_B");
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().get("code")).isEqualTo("CONFLICT");

        // 3. Mauvaise ville : logement Lyon associé à VILLE_A (Paris) → 400
        ResponseEntity<Map> badCity = patchVille(token, logement2, "VILLE_A");
        assertThat(badCity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String registerConfirmedAlternant(String email, String villeA, String villeB) {
        restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, "Password123!", "Alice", "Martin", UserRole.ALTERNANT),
                Map.class);
        String token = jdbcTemplate.queryForObject(
                "SELECT t.token FROM email_confirmation_tokens t JOIN users u ON u.id=t.user_id " +
                "WHERE u.email=? ORDER BY t.created_at DESC LIMIT 1", String.class, email);
        restTemplate.postForEntity("/api/v1/auth/confirm?token=" + token, null, Map.class);

        String jwt = login(email);
        // Crée le profil alternant via l'API
        HttpEntity<Map<String, Object>> profileReq = new HttpEntity<>(Map.of(
                "villeA", villeA, "villeB", villeB,
                "ecole", "YNOV", "entreprise", "TechCorp",
                "dateDebut", "2026-09-07", "dateFin", "2027-07-12",
                "rythme", "SEMAINE_3_1"), authHeaders(jwt));
        restTemplate.postForEntity("/api/v1/profile/alternant", profileReq, Map.class);
        return jwt;
    }

    private String login(String email) {
        Map body = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "Password123!"), Map.class).getBody();
        return body.get("accessToken").toString();
    }

    private UUID createLogement(String token, String ville, String cp) {
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of(
                "adresse", "1 rue Test", "ville", ville, "codePostal", cp,
                "type", "STUDIO", "surface", 25, "nbPieces", 1,
                "loyer", 600, "charges", 0, "isMeuble", true), authHeaders(token));
        Map body = restTemplate.postForEntity("/api/v1/logements", req, Map.class).getBody();
        return UUID.fromString(body.get("id").toString());
    }

    private ResponseEntity<Map> patchVille(String token, UUID logementId, String ville) {
        HttpEntity<Map<String, Object>> req =
                new HttpEntity<>(Map.of("villeAssociee", ville), authHeaders(token));
        return restTemplate.exchange(
                "/api/v1/logements/" + logementId + "/ville",
                HttpMethod.PATCH, req, Map.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
