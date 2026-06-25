package com.studup.backend.scheduler;

import com.studup.backend.service.ReputationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalcule les scores de réputation de tous les utilisateurs chaque nuit à 2h.
 */
@Component
public class ReputationCalculationJob {

    private static final Logger log = LoggerFactory.getLogger(ReputationCalculationJob.class);

    private final ReputationService reputationService;

    public ReputationCalculationJob(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "ReputationCalculationJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void recalculateReputationScores() {
        log.info("ReputationCalculationJob : début du recalcul des scores de réputation");
        reputationService.recalculateAll();
        log.info("ReputationCalculationJob : terminé");
    }
}
