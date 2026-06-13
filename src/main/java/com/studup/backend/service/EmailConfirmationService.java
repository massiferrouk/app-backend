package com.studup.backend.service;

import com.studup.backend.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
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
}
