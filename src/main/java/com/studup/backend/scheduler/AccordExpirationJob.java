package com.studup.backend.scheduler;

import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.repository.AccordRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Expire automatiquement les accords EN_ATTENTE depuis plus de 72h.
 * Tourne toutes les heures. ShedLock garantit une seule exécution par heure.
 */
@Component
public class AccordExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(AccordExpirationJob.class);

    private final AccordRepository accordRepository;

    public AccordExpirationJob(AccordRepository accordRepository) {
        this.accordRepository = accordRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "AccordExpirationJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    @Transactional
    public void expireOldPendingAccords() {
        OffsetDateTime limite = OffsetDateTime.now().minusHours(72);
        int nbExpires = accordRepository.expireAccordsEnAttente(limite, AccordStatut.ANNULE);
        if (nbExpires > 0) {
            log.info("AccordExpirationJob : {} accord(s) expiré(s)", nbExpires);
        }
    }
}
