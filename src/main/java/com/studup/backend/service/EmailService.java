package com.studup.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromEmail;

    public EmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${app.mail.from:noreply@studup.fr}") String fromEmail) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
    }

    // Envoie un email HTML généré depuis un template Thymeleaf
    public void sendHtml(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            String html = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Email envoyé à {} — sujet : {}", to, subject);
        } catch (Exception e) {
            // Best-effort : un échec d'envoi (SMTP injoignable, auth invalide,
            // template en erreur…) ne doit JAMAIS casser le flux appelant
            // (ex. l'inscription). On journalise et on continue. mailSender.send()
            // lève une MailException RUNTIME, non couverte par MessagingException
            // seule — d'où le catch large (corrigé APP-116).
            log.error("Échec envoi email à {} : {}", to, e.getMessage());
        }
    }
}
