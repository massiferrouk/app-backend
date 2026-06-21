package com.studup.backend.service;

import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MatchingSuggestionResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class MatchingService {

    private static final int MAX_SUGGESTIONS = 20;

    private final AlternantProfileRepository profileRepository;
    private final AlternanceScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final CompatibilityCalculator calculator;

    public MatchingService(AlternantProfileRepository profileRepository,
                           AlternanceScheduleRepository scheduleRepository,
                           UserRepository userRepository,
                           CompatibilityCalculator calculator) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.calculator = calculator;
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
}
