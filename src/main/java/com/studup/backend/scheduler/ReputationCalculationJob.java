package com.studup.backend.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalcule les scores de réputation de tous les utilisateurs chaque nuit à 2h.
 * Sera enrichi quand le service de réputation sera implémenté (Sprint 4).
 */
@Component
public class ReputationCalculationJob {

    private static final Logger log = LoggerFactory.getLogger(ReputationCalculationJob.class);

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "ReputationCalculationJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void recalculateReputationScores() {
        log.info("ReputationCalculationJob : début du recalcul des scores de réputation");
        // TODO Sprint 4 : appel ReputationService.recalculateAll()
        log.info("ReputationCalculationJob : terminé");
    }
}
