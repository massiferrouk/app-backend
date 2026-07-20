package com.studup.backend.integration;

import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * APP-117 (A-05) : modification du profil alternant sur vraie base PostgreSQL.
 *
 * Régression du bug de flush Hibernate : updateProfile fait deleteByProfileId()
 * puis réinsère les semaines dans la même transaction. Les dates ne changeant pas,
 * les nouvelles semaines réutilisent les mêmes couples (profile_id, semaine). Sans
 * le flush explicite entre le DELETE et les INSERT, Hibernate ordonne les INSERT
 * avant les DELETE au commit → violation de uq_alternance_schedule → 500.
 *
 * Ce bug NE se reproduit QUE sur une vraie BDD : un test Mockito ne déclenche pas
 * le flush réel. On assert donc explicitement le 200 du PUT (le test échouerait
 * avec un 500 si la correction était retirée).
 */
class AlternantProfileUpdateIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUpdateProfileRegeneratingScheduleWithoutUniqueViolation() {
        // Villes uniques : évite de matcher les profils laissés par d'autres tests
        // (base Testcontainers partagée) et le chemin de notification qui va avec.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String villeA = "AlphaVille" + suffix;
        String villeB = "BetaVille" + suffix;
        String email = "updA_" + UUID.randomUUID() + "@studup.fr";

        String jwt = registerConfirmedAlternantWithProfile(email, villeA, villeB, "SEMAINE_3_1");

        // Le calendrier a bien été généré à la création.
        int semainesAvant = scheduleCount(email);
        assertThat(semainesAvant).as("calendrier généré à la création").isPositive();

        // Modification : on change le rythme ET la première semaine, en gardant les
        // MÊMES dates → le générateur reproduit exactement les mêmes lundis. C'est le
        // scénario exact qui déclenchait le 500 (collision profile_id + semaine).
        ResponseEntity<Map> put = updateProfile(jwt, villeA, villeB, "SEMAINE_1_1", "ECOLE");

        // Cœur de la régression A-05 : avant le fix, ce PUT renvoyait 500.
        assertThat(put.getStatusCode())
                .as("le PUT ne doit pas violer uq_alternance_schedule -> " + put.getBody())
                .isEqualTo(HttpStatus.OK);

        // Le changement de rythme est bien persisté.
        assertThat(rythmeInDb(email)).isEqualTo("SEMAINE_1_1");

        // Le calendrier a été régénéré proprement : mêmes dates → même nombre de
        // semaines, sans doublon ni reliquat (sinon la contrainte unique aurait sauté).
        assertThat(scheduleCount(email))
                .as("calendrier régénéré à l'identique en nombre")
                .isEqualTo(semainesAvant);

        // Idempotence : une seconde modification passe aussi (pas d'état corrompu).
        ResponseEntity<Map> put2 = updateProfile(jwt, villeA, villeB, "SEMAINE_3_1", "ENTREPRISE");
        assertThat(put2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rythmeInDb(email)).isEqualTo("SEMAINE_3_1");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Integer scheduleCount(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM alternance_schedules s " +
                "JOIN alternant_profiles ap ON ap.id = s.profile_id " +
                "JOIN users u ON u.id = ap.user_id WHERE u.email = ?", Integer.class, email);
    }

    private String rythmeInDb(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT ap.rythme::text FROM alternant_profiles ap " +
                "JOIN users u ON u.id = ap.user_id WHERE u.email = ?", String.class, email);
    }

    /** Enregistre + confirme un alternant, crée son profil, et retourne son JWT. */
    private String registerConfirmedAlternantWithProfile(String email, String villeA,
                                                         String villeB, String rythme) {
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
                profileBody(villeA, villeB, rythme, null, jwt), Map.class);
        assertThat(prof.getStatusCode()).as("create profile " + email + " -> " + prof.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return jwt;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> updateProfile(String jwt, String villeA, String villeB,
                                              String rythme, String premiereSemaine) {
        return restTemplate.exchange("/api/v1/profile/alternant", HttpMethod.PUT,
                profileBody(villeA, villeB, rythme, premiereSemaine, jwt), Map.class);
    }

    private HttpEntity<Map<String, Object>> profileBody(String villeA, String villeB,
                                                        String rythme, String premiereSemaine,
                                                        String jwt) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "villeA", villeA, "villeB", villeB,
                "ecole", "YNOV", "entreprise", "TechCorp",
                "dateDebut", "2026-09-07", "dateFin", "2027-07-12",
                "rythme", rythme));
        if (premiereSemaine != null) body.put("premiereSemaine", premiereSemaine);
        return new HttpEntity<>(body, authHeaders(jwt));
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
