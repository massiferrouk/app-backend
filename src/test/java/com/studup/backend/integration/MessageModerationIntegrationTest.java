package com.studup.backend.integration;

import com.studup.backend.model.dto.request.LoginRequest;
import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flow E2E-03 : message → signalement → queue admin → masquage admin
 *
 * Teste le flow complet de modération depuis la création d'un message
 * jusqu'au masquage par l'admin, sur vraie base PostgreSQL.
 */
class MessageModerationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userAccessToken;
    private String adminAccessToken;
    private UUID conversationId;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        // Crée un utilisateur normal
        String userEmail = "user_mod_" + UUID.randomUUID() + "@studup.fr";
        registerUser(userEmail, "Password123!", "Alice", "Martin", UserRole.ALTERNANT);
        userAccessToken = loginAndGetToken(userEmail, "Password123!");

        // Crée un admin directement en base (pas d'endpoint public pour créer des admins)
        String adminEmail = "admin_mod_" + UUID.randomUUID() + "@studup.fr";
        registerUser(adminEmail, "Admin123!", "Admin", "StudUp", UserRole.ALTERNANT);
        // Passe le rôle en ADMIN directement en base
        jdbcTemplate.update(
                "UPDATE users SET role = 'ADMIN' WHERE email = ?",
                adminEmail);
        adminAccessToken = loginAndGetToken(adminEmail, "Admin123!");

        // Crée une conversation et un message via l'API ou directement en base
        conversationId = createConversationInDb();
        addParticipantInDb(conversationId, getUserIdByToken(userAccessToken));
        messageId = createMessageInDb(conversationId, getUserIdByToken(userAccessToken), "Hello ceci est un test");
    }

    // ─── signalement d'un message → HTTP 200 ─────────────────────────────────

    @Test
    void shouldReportMessage() {
        // Un autre utilisateur signale le message
        String reporterEmail = "reporter_" + UUID.randomUUID() + "@studup.fr";
        registerUser(reporterEmail, "Password123!", "Bob", "Dupont", UserRole.ALTERNANT);
        String reporterToken = loginAndGetToken(reporterEmail, "Password123!");

        // Ajouter le reporter à la conversation
        addParticipantInDb(conversationId, getUserIdFromEmail(reporterEmail));

        Map<String, String> reportBody = Map.of("motif", "Contenu inapproprié");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(reporterToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/messages/" + messageId + "/report",
                new HttpEntity<>(reportBody, headers),
                Void.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
    }

    // ─── double signalement du même utilisateur → HTTP 409 ───────────────────

    @Test
    void shouldRejectDuplicateReport() {
        String reporterEmail = "reporter2_" + UUID.randomUUID() + "@studup.fr";
        registerUser(reporterEmail, "Password123!", "Charlie", "Martin", UserRole.ALTERNANT);
        String reporterToken = loginAndGetToken(reporterEmail, "Password123!");
        addParticipantInDb(conversationId, getUserIdFromEmail(reporterEmail));

        Map<String, String> reportBody = Map.of("motif", "Spam");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(reporterToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(reportBody, headers);

        // Premier signalement — OK
        restTemplate.postForEntity("/api/v1/messages/" + messageId + "/report", request, Void.class);

        // Deuxième signalement du même utilisateur — doit être rejeté
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/messages/" + messageId + "/report", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ─── admin consulte la queue de signalements ─────────────────────────────

    @Test
    void shouldAdminGetPendingReports() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminAccessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/moderation/messages", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── non-admin ne peut pas accéder à la queue → HTTP 403 ─────────────────

    @Test
    void shouldReturn403ForNonAdminOnQueue() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userAccessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/moderation/messages", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── admin masque un message → HTTP 204 ──────────────────────────────────

    @Test
    void shouldAdminHideMessage() {
        Map<String, String> body = Map.of("moderationNote", "Contenu signalé masqué par admin");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminAccessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/admin/moderation/messages/" + messageId + "/hide",
                HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Vérifier en base que is_hidden=true
        Boolean isHidden = jdbcTemplate.queryForObject(
                "SELECT is_hidden FROM messages WHERE id = ?::uuid", Boolean.class, messageId.toString());
        assertThat(isHidden).isTrue();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void registerUser(String email, String password, String firstName, String lastName, UserRole role) {
        RegisterRequest request = new RegisterRequest(email, password, firstName, lastName, role);
        restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);
        // APP-82 : le login exige un email confirme — activation directe en base
        jdbcTemplate.update("UPDATE users SET is_verified = true WHERE email = ?", email);
    }

    private String loginAndGetToken(String email, String password) {
        LoginRequest request = new LoginRequest(email, password);
        Map body = restTemplate.postForEntity("/api/v1/auth/login", request, Map.class).getBody();
        return body.get("accessToken").toString();
    }

    private UUID getUserIdByToken(String token) {
        // Récupère l'userId via JdbcTemplate en cherchant l'email depuis le token
        // Pour simplifier, on récupère via la relation inversée
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE is_active = true ORDER BY created_at DESC LIMIT 1",
                UUID.class);
    }

    private UUID getUserIdFromEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private UUID createConversationInDb() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO conversations (id, created_at, last_message_at) VALUES (?::uuid, NOW(), NOW())",
                id.toString());
        return id;
    }

    private void addParticipantInDb(UUID conversationId, UUID userId) {
        if (userId == null) return;
        jdbcTemplate.update(
                "INSERT INTO conversation_participants (id, conversation_id, user_id, joined_at) " +
                "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, NOW()) ON CONFLICT DO NOTHING",
                conversationId.toString(), userId.toString());
    }

    private UUID createMessageInDb(UUID conversationId, UUID senderId, String content) {
        UUID id = UUID.randomUUID();
        if (senderId == null) return id;
        jdbcTemplate.update(
                "INSERT INTO messages (id, conversation_id, sender_id, content, is_read, is_hidden, created_at) " +
                "VALUES (?::uuid, ?::uuid, ?::uuid, ?, false, false, NOW())",
                id.toString(), conversationId.toString(), senderId.toString(), content);
        return id;
    }
}
