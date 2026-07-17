package com.studup.backend.service;

import com.studup.backend.algorithm.ColocationMatcher;
import com.studup.backend.algorithm.ColocationProposal;
import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.algorithm.PartialExchangeOptimizer;
import com.studup.backend.algorithm.PartialExchangeProposal;
import com.studup.backend.algorithm.Scenario;
import com.studup.backend.algorithm.ScenarioAdvisor;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.ColocationResponse;
import com.studup.backend.model.dto.response.MatchingSuggestionResponse;
import com.studup.backend.model.dto.response.PartialExchangeResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MatchingService {

    private static final int MAX_SUGGESTIONS = 20;

    private final AlternantProfileRepository profileRepository;
    private final AlternanceScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final LogementRepository logementRepository;
    private final CompatibilityCalculator calculator;
    private final PartialExchangeOptimizer partialExchangeOptimizer;
    private final ColocationMatcher colocationMatcher;
    private final ScenarioAdvisor scenarioAdvisor;

    public MatchingService(AlternantProfileRepository profileRepository,
                           AlternanceScheduleRepository scheduleRepository,
                           UserRepository userRepository,
                           LogementRepository logementRepository,
                           CompatibilityCalculator calculator,
                           PartialExchangeOptimizer partialExchangeOptimizer,
                           ColocationMatcher colocationMatcher,
                           ScenarioAdvisor scenarioAdvisor) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.logementRepository = logementRepository;
        this.calculator = calculator;
        this.partialExchangeOptimizer = partialExchangeOptimizer;
        this.colocationMatcher = colocationMatcher;
        this.scenarioAdvisor = scenarioAdvisor;
    }

    @Transactional(readOnly = true)
    public List<MatchingSuggestionResponse> getSuggestions(String email) {
        // Récupère le profil de l'utilisateur connecté
        AlternantProfile myProfile = profileRepository.findByUserId(
                userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"))
                        .getId()
        ).orElseThrow(() -> new ResourceNotFoundException(
                "Profil alternant introuvable — créez votre profil avant de consulter les suggestions"));

        // Présélection : uniquement les profils qui partagent au moins une ville
        List<AlternantProfile> candidates = profileRepository.findCandidatesWithSharedCity(
                myProfile.getId(),
                myProfile.getVilleA(),
                myProfile.getVilleB()
        );

        List<AlternanceSchedule> mySchedules = scheduleRepository
                .findByProfileIdOrderBySemaineAsc(myProfile.getId());

        // Logement publié et associé de l'utilisateur connecté (initiateur).
        // Calculé une seule fois — il est le même pour tous les candidats.
        // On garde l'entité complète : son loyer sert au calcul d'économie (APP-103).
        Logement myLogement = findLogementPublieAssocie(myProfile.getUser().getId());
        UUID myLogementId = myLogement == null ? null : myLogement.getId();

        return candidates.stream()
                .map(candidate -> {
                    List<AlternanceSchedule> candidateSchedules = scheduleRepository
                            .findByProfileIdOrderBySemaineAsc(candidate.getId());

                    Logement candidateLogement =
                            findLogementPublieAssocie(candidate.getUser().getId());
                    UUID candidateLogementId =
                            candidateLogement == null ? null : candidateLogement.getId();

                    // Les logements alimentent le calcul d'économie estimée
                    MatchingResult result = calculator.calculate(
                            myProfile, candidate, mySchedules, candidateSchedules,
                            myLogement, candidateLogement);

                    // Match ACTIF = un accord est signable immédiatement.
                    boolean isMatchActif = isMatchActif(
                            result.typePropose(), myLogement, candidateLogement);

                    // Scénarios d'arrangement (APP-109) — messages conditionnels
                    // et options de réorganisation (surplus même ville...)
                    List<Scenario> scenarios = scenarioAdvisor.advise(
                            result, myProfile, candidate, myLogement, candidateLogement);

                    return MatchingSuggestionResponse.from(
                            candidate, result, isMatchActif,
                            myLogementId, candidateLogementId, scenarios);
                })
                // Filtre les profils sans aucune compatibilité (typePropose null)
                .filter(s -> s.typePropose() != null)
                // Matchs actifs en premier, puis tri par score décroissant
                .sorted(Comparator
                        .comparing(MatchingSuggestionResponse::isMatchActif).reversed()
                        .thenComparingDouble(MatchingSuggestionResponse::score).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartialExchangeResponse getPartialExchange(UUID userId1, UUID userId2) {
        AlternantProfile profileA = profileRepository.findByUserId(userId1)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable pour l'utilisateur " + userId1));

        AlternantProfile profileB = profileRepository.findByUserId(userId2)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable pour l'utilisateur " + userId2));

        var schedulesA = scheduleRepository.findByProfileIdOrderBySemaineAsc(profileA.getId());
        var schedulesB = scheduleRepository.findByProfileIdOrderBySemaineAsc(profileB.getId());

        // loyerMensuel null : économie calculée à zéro (logements pas encore liés aux profils)
        PartialExchangeProposal proposal = partialExchangeOptimizer.optimize(
                profileA, profileB, schedulesA, schedulesB, null);

        return PartialExchangeResponse.from(proposal);
    }

    @Transactional(readOnly = true)
    public ColocationResponse getColocation(UUID userId1, UUID userId2) {
        AlternantProfile profileA = profileRepository.findByUserId(userId1)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable pour l'utilisateur " + userId1));

        AlternantProfile profileB = profileRepository.findByUserId(userId2)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil alternant introuvable pour l'utilisateur " + userId2));

        var schedulesA = scheduleRepository.findByProfileIdOrderBySemaineAsc(profileA.getId());
        var schedulesB = scheduleRepository.findByProfileIdOrderBySemaineAsc(profileB.getId());

        // Loyers null : économie zéro jusqu'à ce que les logements soient liés aux profils
        ColocationProposal proposal = colocationMatcher.match(
                profileA, profileB, schedulesA, schedulesB, null, null);

        return ColocationResponse.from(proposal);
    }

    // ─── Méthodes privées ─────────────────────────────────────────────────────

    /**
     * Retourne le premier logement PUBLIÉ (statut ACTIF) et associé à une
     * ville de l'alternant, ou null s'il n'en a pas encore publié.
     * L'entité complète est retournée : le loyer sert au calcul d'économie.
     *
     * On charge tous les logements du propriétaire puis on filtre en mémoire :
     * les colonnes statut et ville_associee sont des ENUM PostgreSQL natifs avec
     * @ColumnTransformer en écriture seule — une requête dérivée
     * (WHERE statut = ?) provoquerait l'erreur SQL 42883 (cf. APP-91).
     */
    private Logement findLogementPublieAssocie(UUID ownerId) {
        return logementRepository.findByOwnerId(ownerId).stream()
                .filter(l -> l.getStatut() == LogementStatut.ACTIF)
                .filter(l -> l.getVilleAssociee() != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * Un match est ACTIF quand un accord peut être signé immédiatement.
     * Pour un échange (total ou partiel), les deux alternants doivent avoir
     * publié leur logement DANS DES VILLES DIFFÉRENTES : deux logements dans
     * la même ville ne permettent pas un échange croisé (bug corrigé en
     * APP-109 — ce cas est un surplus, géré par le moteur de scénarios).
     */
    private boolean isMatchActif(AccordType type, Logement myLogement, Logement candidateLogement) {
        if (type == AccordType.ECHANGE_TOTAL || type == AccordType.ECHANGE_PARTIEL) {
            return myLogement != null && candidateLogement != null
                    && !myLogement.getVille().equalsIgnoreCase(candidateLogement.getVille());
        }
        // Colocation et autres types : au moins un logement disponible côté candidat
        return candidateLogement != null;
    }
}
