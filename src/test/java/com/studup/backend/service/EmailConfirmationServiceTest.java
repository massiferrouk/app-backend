package com.studup.backend.service;

import com.studup.backend.exception.InvalidTokenException;
import com.studup.backend.model.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailConfirmationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailConfirmationService service;

    private final UUID userId = UUID.randomUUID();

    // ─── Envoi de l'email de confirmation (APP-116) ──────────────────────────

    @Test
    void shouldPersistTokenAndSendConfirmationEmail() {
        User user = User.builder()
                .id(userId)
                .email("alice@studup.fr")
                .firstName("Alice")
                .build();

        service.sendConfirmationEmail(user);

        // Un token est inséré en base…
        verify(jdbcTemplate).update(contains("INSERT INTO email_confirmation_tokens"),
                eq(userId), anyString(), any());
        // …et l'email réel est envoyé via le bon template, à la bonne adresse
        verify(emailService).sendHtml(
                eq("alice@studup.fr"),
                anyString(),
                eq("email-confirmation"),
                any());
    }

    /// Simule un SELECT qui ne trouve aucun token
    @SuppressWarnings("unchecked")
    private void stubNoRow() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of());
    }

    // ─── Cas nominal ──────────────────────────────────────────────────────────

    @Test
    void shouldConfirmValidTokenAndVerifyUser() {
        // Un token valide : non utilisé, expire dans 23h
        stubValidRow(OffsetDateTime.now().plusHours(23), null);

        service.confirmToken("token-valide");

        // Le token est consommé puis le compte activé
        verify(jdbcTemplate).update(contains("SET used_at"), eq("token-valide"));
        verify(jdbcTemplate).update(contains("SET is_verified"), eq(userId));
    }

    // ─── Cas d'erreur ─────────────────────────────────────────────────────────

    @Test
    void shouldRejectUnknownToken() {
        stubNoRow(); // aucun résultat en base

        assertThatThrownBy(() -> service.confirmToken("token-inconnu"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("invalide");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void shouldRejectAlreadyUsedToken() {
        stubValidRow(OffsetDateTime.now().plusHours(23), OffsetDateTime.now().minusMinutes(5));

        assertThatThrownBy(() -> service.confirmToken("token-deja-utilise"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("déjà été utilisé");
    }

    @Test
    void shouldRejectExpiredToken() {
        stubValidRow(OffsetDateTime.now().minusHours(1), null);

        assertThatThrownBy(() -> service.confirmToken("token-expire"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expiré");
    }

    // ─── Helper : stub du SELECT avec un RowMapper réel ──────────────────────

    /// Stubbe le SELECT en exécutant le vrai RowMapper du service sur un
    /// ResultSet simulé — le mapping colonnes → record est donc aussi testé.
    @SuppressWarnings("unchecked")
    private void stubValidRow(OffsetDateTime expiresAt, OffsetDateTime usedAt) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenAnswer(invocation -> {
                    RowMapper<Object> mapper = invocation.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("user_id", UUID.class)).thenReturn(userId);
                    when(rs.getObject("expires_at", OffsetDateTime.class)).thenReturn(expiresAt);
                    when(rs.getObject("used_at", OffsetDateTime.class)).thenReturn(usedAt);
                    return List.of(mapper.mapRow(rs, 0));
                });
    }
}
