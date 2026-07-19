package com.studup.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envoi d'emails via l'API HTTP de SendGrid (APP-116).
 *
 * On n'utilise PAS le SMTP : l'hébergeur (Railway) bloque les ports SMTP
 * sortants (25, 587, 2525), ce qui faisait pendre l'envoi indéfiniment.
 * L'API web de SendGrid passe par HTTPS (port 443), jamais bloqué.
 *
 * La clé API est lue depuis la variable déjà configurée (SMTP_PASSWORD).
 * Sans clé (dev, tests), l'envoi est simplement désactivé — jamais bloquant.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String SENDGRID_URL = "https://api.sendgrid.com/v3/mail/send";

    private final TemplateEngine templateEngine;
    private final String fromEmail;
    private final String apiKey;
    private final RestClient restClient;

    public EmailService(
            TemplateEngine templateEngine,
            @Value("${app.mail.from:noreply@studup.fr}") String fromEmail,
            @Value("${app.sendgrid.api-key:}") String apiKey) {
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
        this.apiKey = apiKey;

        // Timeouts courts : un envoi ne doit jamais bloquer le flux appelant
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Envoie un email HTML généré depuis un template Thymeleaf. */
    public void sendHtml(String to, String subject, String templateName, Map<String, Object> variables) {
        // Pas de clé configurée (dev / tests) → on n'envoie rien
        if (apiKey == null || apiKey.isBlank()) {
            log.info("Envoi email désactivé (clé SendGrid absente) — destinataire {}", to);
            return;
        }
        try {
            Context context = new Context();
            context.setVariables(variables);
            String html = templateEngine.process(templateName, context);

            // Corps attendu par l'API SendGrid v3 (mail/send)
            Map<String, Object> payload = Map.of(
                    "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                    "from", Map.of("email", fromEmail),
                    "subject", subject,
                    "content", List.of(Map.of("type", "text/html", "value", html)));

            restClient.post()
                    .uri(SENDGRID_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email envoyé à {} — sujet : {}", to, subject);
        } catch (Exception e) {
            // Best-effort : un échec d'envoi ne doit JAMAIS casser le flux appelant
            log.error("Échec envoi email à {} : {}", to, e.getMessage());
        }
    }
}
