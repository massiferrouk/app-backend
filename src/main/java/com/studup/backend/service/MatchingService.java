package com.studup.backend.service;

import com.studup.backend.algorithm.ColocationMatcher;
import com.studup.backend.algorithm.ColocationProposal;
import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.algorithm.PartialExchangeOptimizer;
import com.studup.backend.algorithm.PartialExchangeProposal;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.ColocationResponse;
import com.studup.backend.model.dto.response.MatchingSuggestionResponse;
import com.studup.backend.model.dto.response.PartialExchangeResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
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
    private final CompatibilityCalculator calculator;
    private final PartialExchangeOptimizer partialExchangeOptimizer;
    private final ColocationMatcher colocationMatcher;

    public MatchingService(AlternantProfileRepository profileRepository,
                           AlternanceScheduleRepository scheduleRepository,
                           UserRepository userRepository,
                           CompatibilityCalculator calculator,
                           PartialExchangeOptimizer partialExchangeOptimizer,
                           ColocationMatcher colocationMatcher) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.calculator = calculator;
        this.partialExchangeOptimizer = partialExchangeOptimizer;
        this.colocationMatcher = colocationMatcher;
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

        return candidates.stream()
                .map(candidate -> {
                    List<AlternanceSchedule> candidateSchedules = scheduleRepository
                            .findByProfileIdOrderBySemaineAsc(candidate.getId());

                    MatchingResult result = calculator.calculate(
                            myProfile, candidate, mySchedules, candidateSchedules);

                    return MatchingSuggestionResponse.from(candidate, result);
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
}
