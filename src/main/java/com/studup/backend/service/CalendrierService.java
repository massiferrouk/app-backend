package com.studup.backend.service;

import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CalendrierService {

    private final AlternantProfileRepository profileRepository;
    private final AlternanceScheduleRepository scheduleRepository;
    private final CompatibilityCalculator calculator;

    public CalendrierService(AlternantProfileRepository profileRepository,
                             AlternanceScheduleRepository scheduleRepository,
                             CompatibilityCalculator calculator) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.calculator = calculator;
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

        return calculator.calculate(profileA, profileB, schedulesA, schedulesB).semaines();
    }
}
