package com.studup.backend.integration;

import com.studup.backend.model.dto.request.LoginRequest;
import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Espace d'administration de bout en bout (APP-121).
 *
 * Ces tests manquaient : AdminServiceTest mocke le repository et
 * AdminControllerTest mocke le service, si bien que la requête de listing des
 * comptes n'était jamais réellement exécutée contre PostgreSQL. Elle échouait
 * en production sur le typage des ENUM natifs, sans qu'aucun test ne le voie.
 */
class AdminIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;

    @BeforeEach
    void setUp() {
        String adminEmail = "admin_it_" + UUID.randomUUID() + "@studup.fr";
        registerUser(adminEmail, "Admin123!", UserRole.ALTERNANT);
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", adminEmail);
        adminToken = loginAndGetToken(adminEmail, "Admin123!");

        // Un compte de chaque rôle filtrable, pour que les filtres aient du sens
        registerUser("alt_it_" + UUID.randomUUID() + "@studup.fr", "Password123!",
                UserRole.ALTERNANT);
        registerUser("etu_it_" + UUID.randomUUID() + "@studup.fr", "Password123!",
                UserRole.ETUDIANT);
    }

    // ─── GET /admin/users ─────────────────────────────────────────────────────

    @Test
    void shouldListerLesComptesSansAucunFiltre() {
        // Cas qui échouait : sans filtre, les paramètres partent à null et
        // PostgreSQL ne sait pas les typer face à une colonne user_role.
        ResponseEntity<Map> response = get("/api/v1/admin/users");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List<?>) response.getBody().get("content")).isNotEmpty();
    }

    @Test
    void shouldFiltrerLesComptesParRole() {
        ResponseEntity<Map> response = get("/api/v1/admin/users?role=ETUDIANT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        java.util.List<?> content = (java.util.List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(ligne ->
                assertThat(((Map<?, ?>) ligne).get("role")).isEqualTo("ETUDIANT"));
    }

    @Test
    void shouldFiltrerLesComptesParEtatActif() {
        ResponseEntity<Map> response = get("/api/v1/admin/users?isActive=true");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List<?>) response.getBody().get("content")).isNotEmpty();
    }

    @Test
    void shouldCombinerLesDeuxFiltres() {
        ResponseEntity<Map> response =
                get("/api/v1/admin/users?role=ALTERNANT&isActive=true");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── Tableau de bord et files de modération ───────────────────────────────

    @Test
    void shouldRetournerLeTableauDeBord() {
        ResponseEntity<Map> response = get("/api/v1/admin/dashboard");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Les répartitions sont complètes même sans donnée : un rôle sans
        // compte doit valoir 0, pas disparaître de la réponse.
        @SuppressWarnings("unchecked")
        Map<String, Object> parRole =
                (Map<String, Object>) response.getBody().get("comptesParRole");
        assertThat(parRole).containsKeys("ALTERNANT", "ETUDIANT", "PROPRIETAIRE", "ADMIN");
    }

    @Test
    void shouldRetournerLesFilesDeModerationVides() {
        assertThat(get("/api/v1/admin/moderation/messages").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/admin/moderation/logements").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/admin/logements").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldFiltrerLesAnnoncesParStatut() {
        // Même piège d'ENUM natif que pour les comptes, sur logement_statut
        assertThat(get("/api/v1/admin/logements?statut=ACTIF").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ─── Sécurité de l'accès administrateur ───────────────────────────────────

    @Test
    void shouldRefuserUneInscriptionEnAdmin() {
        // L'attaque réelle : contourner le formulaire et poster le rôle ADMIN
        // directement sur l'API. Sans contrôle serveur, le compte était créé
        // avec tous les droits.
        String email = "pirate_" + UUID.randomUUID() + "@studup.fr";
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(email, "Password123!", "Pi", "Rate", UserRole.ADMIN),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Et surtout : aucun compte ne doit exister en base
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        assertThat(count).isZero();
    }

    @Test
    void shouldRefuserToutesLesRoutesAdminSansRoleAdmin() {
        String email = "simple_" + UUID.randomUUID() + "@studup.fr";
        registerUser(email, "Password123!", UserRole.ETUDIANT);
        String tokenSimple = loginAndGetToken(email, "Password123!");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenSimple);
        HttpEntity<Void> requete = new HttpEntity<>(headers);

        // Balayage de toutes les portes d'entrée de l'administration
        for (String url : java.util.List.of(
                "/api/v1/admin/dashboard",
                "/api/v1/admin/users",
                "/api/v1/admin/logements",
                "/api/v1/admin/moderation/messages",
                "/api/v1/admin/moderation/logements",
                "/api/v1/admin/moderation/mots-interdits")) {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, requete, Map.class);
            assertThat(response.getStatusCode())
                    .as("route %s doit être refusée à un non-admin", url)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void shouldRefuserLesRoutesAdminSansAuthentification() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/users", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldEmpecherUnUtilisateurDeSeHisserAdminParChangementDeMode() {
        String email = "grimpeur_" + UUID.randomUUID() + "@studup.fr";
        registerUser(email, "Password123!", UserRole.ETUDIANT);
        String token = loginAndGetToken(email, "Password123!");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Le changement de mode existe pour basculer étudiant ⇄ alternant :
        // il ne doit jamais servir d'ascenseur vers ADMIN.
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/users/me/role", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("role", "ADMIN"), headers), Map.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);

        String roleEnBase = jdbcTemplate.queryForObject(
                "SELECT role::text FROM users WHERE email = ?", String.class, email);
        assertThat(roleEnBase).isEqualTo("ETUDIANT");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<Map> get(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private void registerUser(String email, String password, UserRole role) {
        restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, password, "Prenom", "Nom", role), Map.class);
        // Le login exige un email confirmé — activation directe en base
        jdbcTemplate.update("UPDATE users SET is_verified = true WHERE email = ?", email);
    }

    private String loginAndGetToken(String email, String password) {
        Map body = restTemplate.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, password), Map.class).getBody();
        return body.get("accessToken").toString();
    }
}
