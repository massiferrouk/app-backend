package com.studup.backend.scheduler;

import com.studup.backend.model.entity.User;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.MessageRepository;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.service.EmailService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EmailDigestJob {

    private static final Logger log = LoggerFactory.getLogger(EmailDigestJob.class);

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final AccordRepository accordRepository;
    private final EmailService emailService;
    private final String appBaseUrl;

    public EmailDigestJob(
            UserRepository userRepository,
            MessageRepository messageRepository,
            AccordRepository accordRepository,
            EmailService emailService,
            @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.accordRepository = accordRepository;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl;
    }

    // Cron : secondes=0, minutes=0, heures=8, jour=*, mois=*, jour-semaine=1 (lundi)
    @Scheduled(cron = "0 0 8 * * 1")
    @SchedulerLock(name = "EmailDigestJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void sendWeeklyDigest() {
        log.info("EmailDigestJob : début de l'envoi du résumé hebdomadaire");

        List<User> users = userRepository.findByIsActiveTrueAndDeletedAtIsNull();
        int nbEnvoyes = 0;

        for (User user : users) {
            long nbMessages = messageRepository.countUnreadForUser(user.getId());
            long nbEnAttente = accordRepository.findAccordsEnAttenteForReceiver(user.getId()).size();
            long nbProchains = accordRepository.findProchainAccords(
                    user.getId(), LocalDate.now().plusWeeks(2)).size();

            // N'envoie que si l'utilisateur a au moins une activité
            if (nbMessages == 0 && nbEnAttente == 0 && nbProchains == 0) {
                continue;
            }

            Map<String, Object> variables = new HashMap<>();
            variables.put("prenom", user.getFirstName());
            variables.put("nbMessagesNonLus", nbMessages);
            variables.put("nbAccordsEnAttente", nbEnAttente);
            variables.put("nbProchainAccords", nbProchains);
            variables.put("appUrl", appBaseUrl);
            variables.put("unsubscribeUrl",
                    appBaseUrl + "/api/v1/notifications/unsubscribe/" + user.getId());

            emailService.sendHtml(
                    user.getEmail(),
                    "Ton résumé StudUp de la semaine",
                    "email-digest",
                    variables);
            nbEnvoyes++;
        }

        log.info("EmailDigestJob : {} email(s) envoyé(s) sur {} utilisateur(s)", nbEnvoyes, users.size());
    }
}
