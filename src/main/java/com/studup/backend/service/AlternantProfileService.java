package com.studup.backend.service;

import com.studup.backend.exception.ProfileAlreadyExistsException;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateAlternantProfileRequest;
import com.studup.backend.model.dto.response.AlternantProfileResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.RythmeAlternance;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlternantProfileService {

    private final AlternantProfileRepository profileRepository;
    private final AlternanceScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    public AlternantProfileService(AlternantProfileRepository profileRepository,
                                   AlternanceScheduleRepository scheduleRepository,
                                   UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AlternantProfileResponse createProfile(String email, CreateAlternantProfileRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        if (profileRepository.existsByUserId(user.getId())) {
            throw new ProfileAlreadyExistsException("Un profil alternant existe déjà pour ce compte");
        }

        // Validation métier : dateDebut doit être avant dateFin
        if (!request.dateDebut().isBefore(request.dateFin())) {
            throw new IllegalArgumentException("La date de début doit être antérieure à la date de fin");
        }

        // Validation métier : les deux villes doivent être différentes
        if (request.villeA().equalsIgnoreCase(request.villeB())) {
            throw new IllegalArgumentException("La ville A et la ville B doivent être différentes");
        }

        AlternantProfile profile = AlternantProfile.builder()
                .user(user)
                .villeA(request.villeA())
                .villeB(request.villeB())
                .ecole(request.ecole())
                .entreprise(request.entreprise())
                .dateDebut(request.dateDebut())
                .dateFin(request.dateFin())
                .rythme(request.rythme())
                .build();

        profile = profileRepository.save(profile);

        List<AlternanceSchedule> schedule = generateSchedule(profile);
        scheduleRepository.saveAll(schedule);

        return AlternantProfileResponse.from(profile, schedule.size());
    }

    @Transactional(readOnly = true)
    public AlternantProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        AlternantProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profil alternant introuvable"));

        List<AlternanceSchedule> schedule = scheduleRepository.findByProfileIdOrderBySemaineAsc(profile.getId());
        return AlternantProfileResponse.from(profile, schedule.size());
    }

    @Transactional
    public AlternantProfileResponse updateProfile(String email, CreateAlternantProfileRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        AlternantProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profil alternant introuvable"));

        if (!request.dateDebut().isBefore(request.dateFin())) {
            throw new IllegalArgumentException("La date de début doit être antérieure à la date de fin");
        }

        if (request.villeA().equalsIgnoreCase(request.villeB())) {
            throw new IllegalArgumentException("La ville A et la ville B doivent être différentes");
        }

        profile.setVilleA(request.villeA());
        profile.setVilleB(request.villeB());
        profile.setEcole(request.ecole());
        profile.setEntreprise(request.entreprise());
        profile.setDateDebut(request.dateDebut());
        profile.setDateFin(request.dateFin());
        profile.setRythme(request.rythme());

        profile = profileRepository.save(profile);

        // Recalcul complet du calendrier après modification du profil
        scheduleRepository.deleteByProfileId(profile.getId());
        List<AlternanceSchedule> schedule = generateSchedule(profile);
        scheduleRepository.saveAll(schedule);

        return AlternantProfileResponse.from(profile, schedule.size());
    }

    // Génère 52 semaines de calendrier à partir de la date de début du profil.
    // Chaque entrée représente un lundi — le label 'A' ou 'B' dépend du rythme.
    private List<AlternanceSchedule> generateSchedule(AlternantProfile profile) {
        List<AlternanceSchedule> schedules = new ArrayList<>();

        // On part toujours d'un lundi
        LocalDate firstMonday = getFirstMonday(profile.getDateDebut());

        for (int i = 0; i < 52; i++) {
            LocalDate semaine = firstMonday.plusWeeks(i);

            // On s'arrête si on dépasse la date de fin du contrat
            if (semaine.isAfter(profile.getDateFin())) break;

            schedules.add(AlternanceSchedule.builder()
                    .profile(profile)
                    .semaine(semaine)
                    .label(getLabelForWeek(i, profile.getRythme()))
                    .isOverridden(false)
                    .build());
        }

        return schedules;
    }

    // Retourne le lundi de la semaine contenant la date donnée,
    // ou la date elle-même si c'est déjà un lundi.
    private LocalDate getFirstMonday(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.MONDAY) return date;
        return date.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
    }

    // Détermine le label A ou B d'une semaine selon le rythme d'alternance.
    // L'index commence à 0 pour la première semaine du contrat.
    private String getLabelForWeek(int weekIndex, RythmeAlternance rythme) {
        return switch (rythme) {
            // 1 semaine école, 1 semaine entreprise en alternant
            case SEMAINE_1_1 -> weekIndex % 2 == 0 ? "A" : "B";
            // 3 semaines entreprise + 1 semaine école
            case SEMAINE_3_1 -> weekIndex % 4 == 3 ? "A" : "B";
            // 4 semaines école + 4 semaines entreprise (approximation mensuelle)
            case MOIS_1_1 -> weekIndex % 8 < 4 ? "A" : "B";
            // Rythme non standard : on applique le pattern 1/1 par défaut
            case AUTRE -> weekIndex % 2 == 0 ? "A" : "B";
        };
    }
}
