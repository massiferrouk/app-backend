package com.studup.backend.service;

import com.studup.backend.exception.InvalidTokenException;
import com.studup.backend.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EmailConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(EmailConfirmationService.class);

    private final JdbcTemplate jdbcTemplate;

    public EmailConfirmationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void sendConfirmationEmail(User user) {
        String token = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);

        jdbcTemplate.update(
            "INSERT INTO email_confirmation_tokens (user_id, token, expires_at) VALUES (?, ?, ?)",
            user.getId(), token, expiresAt
        );

        // TODO APP-22 : remplacer ce log par un vrai envoi SMTP (SendGrid)
        // On ne logue jamais l'email — uniquement l'userId
        log.info("Token de confirmation généré pour userId={} — lien : /api/v1/auth/confirm?token={}",
                user.getId(), token);
    }

    /// Représentation interne d'un token lu en base
    private record TokenRow(UUID userId, OffsetDateTime expiresAt, OffsetDateTime usedAt) {}

    /**
     * Valide le token puis marque le compte comme vérifié.
     * Règles : le token doit exister, ne pas être expiré (24h),
     * et n'avoir jamais servi (usage unique).
     */
    @Transactional
    public void confirmToken(String token) {
        List<TokenRow> rows = jdbcTemplate.query(
            "SELECT user_id, expires_at, used_at FROM email_confirmation_tokens WHERE token = ?",
            (rs, i) -> new TokenRow(
                rs.getObject("user_id", UUID.class),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("used_at", OffsetDateTime.class)),
            token
        );

        if (rows.isEmpty()) {
            throw new InvalidTokenException("Lien de confirmation invalide");
        }
        TokenRow row = rows.getFirst();

        if (row.usedAt() != null) {
            throw new InvalidTokenException("Ce lien de confirmation a déjà été utilisé");
        }
        if (row.expiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("Ce lien de confirmation a expiré (validité 24h)");
        }

        // Usage unique : le token est consommé AVANT d'activer le compte
        jdbcTemplate.update(
            "UPDATE email_confirmation_tokens SET used_at = NOW() WHERE token = ?", token);
        jdbcTemplate.update(
            "UPDATE users SET is_verified = true WHERE id = ?", row.userId());

        log.info("Email confirmé pour userId={}", row.userId());
    }
}
