package com.studup.backend.integration;

import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * APP-96 (recette) : recherche de logements sur VRAIE base PostgreSQL.
 *
 * La recherche filtre statut = ACTIF (et éventuellement type) via des
 * JPA Specifications. Ces colonnes sont des ENUM PostgreSQL natifs : une
 * comparaison directe « statut = ? » lie un varchar et échoue (42883).
 * Un test unitaire mocké ne l'aurait pas attrapé — bug trouvé en recette
 * (un propriétaire publiait des logements que personne ne voyait).
 */
class LogementSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReturnPublishedLogementsInSearch() {
        String ownerToken = registerConfirmedProprietaire(
                "owner_" + UUID.randomUUID() + "@studup.fr");
        // APP-117 (A-04) : la recherche exclut les logements de l'utilisateur
        // connecté → on cherche avec un SECOND compte, distinct du propriétaire.
        String searcherToken = registerConfirmedProprietaire(
                "searcher_" + UUID.randomUUID() + "@studup.fr");

        // Ville unique pour isoler ce test des autres données
        String ville = "TestVille" + Integer.toHexString((int) (Math.random() * 100000));

        // 2 logements publiés (ACTIF) + 1 laissé en BROUILLON
        publishLogement(ownerToken, ville);
        publishLogement(ownerToken, ville);
        createLogement(ownerToken, ville); // reste BROUILLON, ne doit PAS ressortir

        // Recherche par ville → seuls les 2 ACTIF (le chemin qui plantait en 500)
        ResponseEntity<Map> byVille = restTemplate.exchange(
                "/api/v1/logements?ville=" + ville,
                HttpMethod.GET, new HttpEntity<>(authHeaders(searcherToken)), Map.class);

        assertThat(byVille.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) byVille.getBody().get("content")).hasSize(2);

        // Recherche avec filtre type (ENUM natif aussi) → toujours 2
        ResponseEntity<Map> byType = restTemplate.exchange(
                "/api/v1/logements?ville=" + ville + "&type=STUDIO",
                HttpMethod.GET, new HttpEntity<>(authHeaders(searcherToken)), Map.class);

        assertThat(byType.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) byType.getBody().get("content")).hasSize(2);

        // APP-117 (A-04) : le propriétaire ne voit JAMAIS ses propres logements
        // dans la recherche — il ne peut pas se contacter lui-même.
        ResponseEntity<Map> byOwner = restTemplate.exchange(
                "/api/v1/logements?ville=" + ville,
                HttpMethod.GET, new HttpEntity<>(authHeaders(ownerToken)), Map.class);

        assertThat(byOwner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) byOwner.getBody().get("content")).isEmpty();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String registerConfirmedProprietaire(String email) {
        restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, "Password123!", "Rabah", "Ammour", UserRole.PROPRIETAIRE),
                Map.class);
        String token = jdbcTemplate.queryForObject(
                "SELECT t.token FROM email_confirmation_tokens t JOIN users u ON u.id=t.user_id " +
                "WHERE u.email=? ORDER BY t.created_at DESC LIMIT 1", String.class, email);
        restTemplate.postForEntity("/api/v1/auth/confirm?token=" + token, null, Map.class);
        return login(email);
    }

    private String login(String email) {
        Map body = restTemplate.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "Password123!"), Map.class).getBody();
        return body.get("accessToken").toString();
    }

    private UUID createLogement(String token, String ville) {
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of(
                "adresse", "1 rue Test", "ville", ville, "codePostal", "33000",
                "type", "STUDIO", "surface", 25, "nbPieces", 1,
                "loyer", 600, "charges", 0, "isMeuble", true), authHeaders(token));
        Map body = restTemplate.postForEntity("/api/v1/logements", req, Map.class).getBody();
        return UUID.fromString(body.get("id").toString());
    }

    private void publishLogement(String token, String ville) {
        UUID id = createLogement(token, ville);
        restTemplate.exchange("/api/v1/logements/" + id + "/publish",
                HttpMethod.PUT, new HttpEntity<>(authHeaders(token)), Map.class);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
