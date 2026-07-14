package com.studup.backend.service;

import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.event.AlternantProfileSavedEvent;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.MatchNotification;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.MatchNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envoie une notification « nouveau match » aux alternants compatibles
 * lorsqu'un profil est créé ou modifié (APP-98).
 *
 * On notifie TOUS les matchs (actifs et potentiels) : l'intérêt est de faire
 * savoir aux étudiants/alternants qu'un profil compatible existe, même si les
 * logements ne sont pas encore publiés.
 *
 * Déduplication : une seule notification par PAIRE d'utilisateurs, jamais
 * renotifiée (table match_notifications, paire canonique userAId < userBId).
 */
@Service
public class MatchNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MatchNotificationService.class);

    private final AlternantProfileRepository profileRepository;
    private final AlternanceScheduleRepository scheduleRepository;
    private final CompatibilityCalculator calculator;
    private final MatchNotificationRepository matchNotificationRepository;
    private final NotificationService notificationService;

    public MatchNotificationService(AlternantProfileRepository profileRepository,
                                    AlternanceScheduleRepository scheduleRepository,
                                    CompatibilityCalculator calculator,
                                    MatchNotificationRepository matchNotificationRepository,
                                    NotificationService notificationService) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.calculator = calculator;
        this.matchNotificationRepository = matchNotificationRepository;
        this.notificationService = notificationService;
    }

    /** Écoute la sauvegarde d'un profil — non bloquant en cas d'erreur. */
    @EventListener
    @Transactional
    public void onProfileSaved(AlternantProfileSavedEvent event) {
        try {
            notifyNewMatches(event.userId());
        } catch (Exception e) {
            // La notification est secondaire : elle ne doit jamais faire échouer
            // la création/modification du profil.
            log.warn("Notification de nouveaux matchs échouée pour userId={} : {}",
                    event.userId(), e.getMessage());
        }
    }

    /**
     * Calcule les matchs de l'utilisateur et notifie chaque paire compatible
     * pas encore notifiée (les deux alternants sont prévenus).
     */
    @Transactional
    public void notifyNewMatches(UUID userId) {
        AlternantProfile me = profileRepository.findByUserId(userId).orElse(null);
        if (me == null) return;

        List<AlternantProfile> candidates = profileRepository.findCandidatesWithSharedCity(
                me.getId(), me.getVilleA(), me.getVilleB());

        List<AlternanceSchedule> mySchedules =
                scheduleRepository.findByProfileIdOrderBySemaineAsc(me.getId());

        for (AlternantProfile candidate : candidates) {
            List<AlternanceSchedule> candidateSchedules =
                    scheduleRepository.findByProfileIdOrderBySemaineAsc(candidate.getId());

            MatchingResult result = calculator.calculate(me, candidate, mySchedules, candidateSchedules);

            // typePropose null = aucune compatibilité → pas un match
            if (result.typePropose() == null) continue;

            UUID otherUserId = candidate.getUser().getId();

            // Paire canonique (a < b) — comparaison sur la représentation texte, PAS
            // UUID.compareTo() : ce dernier compare mostSigBits/leastSigBits en signé,
            // alors que PostgreSQL compare le type uuid octet par octet en NON SIGNÉ.
            // Les deux ordres divergent pour ~50% des paires aléatoires (dès que le bit
            // de poids fort d'un octet diffère), ce qui viole la contrainte CHECK
            // chk_match_notif_order côté base une fois sur deux (bug trouvé lors du
            // debug APP-98 : le test d'intégration échouait de façon aléatoire).
            UUID a = userId.toString().compareTo(otherUserId.toString()) < 0 ? userId : otherUserId;
            UUID b = userId.toString().compareTo(otherUserId.toString()) < 0 ? otherUserId : userId;

            if (matchNotificationRepository.existsByUserAIdAndUserBId(a, b)) continue;

            matchNotificationRepository.save(MatchNotification.builder()
                    .userAId(a).userBId(b).build());

            // On prévient les DEUX alternants qu'un match compatible existe
            notificationService.notify(userId, NotificationType.NOUVEAU_MATCH,
                    Map.of("prenom", candidate.getUser().getFirstName()),
                    "match/" + otherUserId);
            notificationService.notify(otherUserId, NotificationType.NOUVEAU_MATCH,
                    Map.of("prenom", me.getUser().getFirstName()),
                    "match/" + userId);
        }
    }
}
