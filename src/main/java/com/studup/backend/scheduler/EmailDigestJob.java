package com.studup.backend.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Envoie le résumé hebdomadaire par email chaque lundi à 8h.
 * Sera enrichi quand le service d'email sera implémenté (Sprint 4).
 */
@Component
public class EmailDigestJob {

    private static final Logger log = LoggerFactory.getLogger(EmailDigestJob.class);

    // Cron : secondes=0, minutes=0, heures=8, jour=*, mois=*, jour-semaine=1 (lundi)
    @Scheduled(cron = "0 0 8 * * 1")
    @SchedulerLock(name = "EmailDigestJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void sendWeeklyDigest() {
        log.info("EmailDigestJob : début de l'envoi du résumé hebdomadaire");
        // TODO Sprint 4 : appel EmailService.sendWeeklyDigest()
        log.info("EmailDigestJob : terminé");
    }
}
