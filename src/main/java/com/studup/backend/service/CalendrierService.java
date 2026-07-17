package com.studup.backend.service;

import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.OverrideScheduleRequest;
import com.studup.backend.model.dto.response.AlternanceScheduleResponse;
import com.studup.backend.model.dto.response.MesSemainesResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CalendrierService {

    private static final Logger log = LoggerFactory.getLogger(CalendrierService.class);

    private final AlternantProfileRepository profileRepository;
    private final AlternanceScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final LogementRepository logementRepository;
    private final CompatibilityCalculator calculator;
    private final RedisTemplate<String, Object> redisTemplate;

    public CalendrierService(AlternantProfileRepository profileRepository,
                             AlternanceScheduleRepository scheduleRepository,
                             UserRepository userRepository,
                             LogementRepository logementRepository,
                             CompatibilityCalculator calculator,
                             RedisTemplate<String, Object> redisTemplate) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.logementRepository = logementRepository;
        this.calculator = calculator;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public List<SemaineCompatibilite> getCalendrierCompatibilite(UUID userId1, UUID userId2) {
        var profileA = profileRepository.findByUserId(userId1)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable pour l'utilisateur " + userId1));

        var profileB = profileRepository.findByUserId(userId2)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable pour l'utilisateur " + userId2));

        var schedulesA = scheduleRepository.findByProfileIdOrderBySemaineAsc(profileA.getId());
        var schedulesB = scheduleRepository.findByProfileIdOrderBySemaineAsc(profileB.getId());

        // Les logements publiés déterminent les semaines d'échange RÉELLES
        // (APP-110) : sans eux, le calendrier n'affiche plus de faux vert
        Logement logementA = findLogementPublieAssocie(userId1);
        Logement logementB = findLogementPublieAssocie(userId2);

        return calculator.calculate(profileA, profileB, schedulesA, schedulesB,
                logementA, logementB).semaines();
    }

    // Même règle que MatchingService : le logement ACTIF associé à une ville du profil
    private Logement findLogementPublieAssocie(UUID ownerId) {
        return logementRepository.findByOwnerId(ownerId).stream()
                .filter(l -> l.getStatut() == LogementStatut.ACTIF)
                .filter(l -> l.getVilleAssociee() != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * Retourne le calendrier d'alternance complet de l'utilisateur connecté,
     * avec le contexte du profil (villes, rythme) pour l'affichage mobile (APP-67).
     */
    @Transactional(readOnly = true)
    public MesSemainesResponse getMesSemaines(String userEmail) {
        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        AlternantProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable — crée d'abord ton profil"));

        var semaines = scheduleRepository
                .findByProfileIdOrderBySemaineAsc(profile.getId())
                .stream()
                .map(AlternanceScheduleResponse::from)
                .toList();

        return new MesSemainesResponse(
                profile.getId(),
                profile.getVilleA(),
                profile.getVilleB(),
                profile.getRythme(),
                semaines);
    }

    /**
     * Modifie manuellement le label d'une semaine du calendrier.
     * Interdit sur les semaines passées.
     * Invalide le cache matching Redis après modification.
     */
    @Transactional
    public AlternanceScheduleResponse overrideSemaine(
            String userEmail, UUID profileId, LocalDate semaine, OverrideScheduleRequest request) {

        // Ownership check — l'utilisateur doit être propriétaire du profil
        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        AlternantProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil alternant introuvable"));

        if (!profile.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Vous ne pouvez modifier que votre propre calendrier");
        }

        // Règle métier : pas de modification sur les semaines passées
        if (semaine.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Impossible de modifier une semaine passée : " + semaine);
        }

        // Recherche de la semaine à modifier
        AlternanceSchedule schedule = scheduleRepository
                .findByProfileIdAndSemaine(profileId, semaine)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Semaine introuvable : " + semaine + " pour ce profil"));

        // Application de l'override
        schedule.setLabel(request.label());
        schedule.setIsOverridden(true);
        schedule.setOverrideReason(request.reason());

        AlternanceSchedule saved = scheduleRepository.save(schedule);

        // Invalidation du cache matching Redis — toutes les clés impliquant ce profil
        invalidateMatchingCache(profileId);

        log.info("Override calendrier — profileId={} semaine={} label={} reason={}",
                profileId, semaine, request.label(), request.reason());

        return AlternanceScheduleResponse.from(saved);
    }

    /**
     * Supprime toutes les clés Redis matching:{...profileId...}.
     * Le cache sera recalculé au prochain appel à /matching/suggestions.
     */
    private void invalidateMatchingCache(UUID profileId) {
        try {
            String pattern = "matching:*" + profileId + "*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Cache matching invalidé — {} clé(s) supprimée(s) pour profileId={}",
                        keys.size(), profileId);
            }
        } catch (Exception e) {
            // L'invalidation du cache ne doit jamais bloquer la modification
            log.warn("Impossible d'invalider le cache matching pour profileId={} : {}",
                    profileId, e.getMessage());
        }
    }
}
