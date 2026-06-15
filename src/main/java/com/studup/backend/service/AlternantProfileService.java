package com.studup.backend.service;

import com.studup.backend.algorithm.ScheduleGenerator;
import com.studup.backend.exception.ProfileAlreadyExistsException;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateAlternantProfileRequest;
import com.studup.backend.model.dto.response.AlternantProfileResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.JourFerie;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.JourFerieRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AlternantProfileService {

    private final AlternantProfileRepository profileRepository;
    private final AlternanceScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final JourFerieRepository jourFerieRepository;
    private final ScheduleGenerator scheduleGenerator;

    public AlternantProfileService(AlternantProfileRepository profileRepository,
                                   AlternanceScheduleRepository scheduleRepository,
                                   UserRepository userRepository,
                                   JourFerieRepository jourFerieRepository,
                                   ScheduleGenerator scheduleGenerator) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.jourFerieRepository = jourFerieRepository;
        this.scheduleGenerator = scheduleGenerator;
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

        List<AlternanceSchedule> schedule = buildAndSaveSchedule(profile);

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
        List<AlternanceSchedule> schedule = buildAndSaveSchedule(profile);

        return AlternantProfileResponse.from(profile, schedule.size());
    }

    // Charge les jours fériés FR sur la période du profil, délègue la génération
    // à ScheduleGenerator, puis persiste toutes les semaines en base.
    private List<AlternanceSchedule> buildAndSaveSchedule(AlternantProfile profile) {
        Set<JourFerie> joursFeries = jourFerieRepository.findByPaysAndDateJourBetween(
                "FR", profile.getDateDebut(), profile.getDateFin()
        );
        List<AlternanceSchedule> schedule = scheduleGenerator.generateSchedule(profile, joursFeries);
        scheduleRepository.saveAll(schedule);
        return schedule;
    }
}
