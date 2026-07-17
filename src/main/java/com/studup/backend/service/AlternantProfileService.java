package com.studup.backend.service;

import com.studup.backend.algorithm.ScheduleGenerator;
import com.studup.backend.event.AlternantProfileSavedEvent;
import com.studup.backend.exception.ProfileAlreadyExistsException;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateAlternantProfileRequest;
import com.studup.backend.model.dto.response.AlternantProfileResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.JourFerie;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.PremiereSemaine;
import com.studup.backend.model.enums.RythmeAlternance;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.JourFerieRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public AlternantProfileService(AlternantProfileRepository profileRepository,
                                   AlternanceScheduleRepository scheduleRepository,
                                   UserRepository userRepository,
                                   JourFerieRepository jourFerieRepository,
                                   ScheduleGenerator scheduleGenerator,
                                   ApplicationEventPublisher eventPublisher) {
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.jourFerieRepository = jourFerieRepository;
        this.scheduleGenerator = scheduleGenerator;
        this.eventPublisher = eventPublisher;
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

        rejeterRythmeAutre(request);

        AlternantProfile profile = AlternantProfile.builder()
                .user(user)
                .villeA(request.villeA())
                .villeB(request.villeB())
                .ecole(request.ecole())
                .entreprise(request.entreprise())
                .dateDebut(request.dateDebut())
                .dateFin(request.dateFin())
                .rythme(request.rythme())
                .premiereSemaine(resolvePremiereSemaine(request))
                .build();

        profile = profileRepository.save(profile);

        List<AlternanceSchedule> schedule = buildAndSaveSchedule(profile);

        // Notifie les alternants compatibles qu'un nouveau match existe (APP-98)
        eventPublisher.publishEvent(new AlternantProfileSavedEvent(user.getId()));

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

        rejeterRythmeAutre(request);

        profile.setVilleA(request.villeA());
        profile.setVilleB(request.villeB());
        profile.setEcole(request.ecole());
        profile.setEntreprise(request.entreprise());
        profile.setDateDebut(request.dateDebut());
        profile.setDateFin(request.dateFin());
        profile.setRythme(request.rythme());
        profile.setPremiereSemaine(resolvePremiereSemaine(request));

        profile = profileRepository.save(profile);

        // Recalcul complet du calendrier après modification du profil
        scheduleRepository.deleteByProfileId(profile.getId());
        List<AlternanceSchedule> schedule = buildAndSaveSchedule(profile);

        // Les villes/rythme ont pu changer → recalcule et notifie les matchs (APP-98)
        eventPublisher.publishEvent(new AlternantProfileSavedEvent(user.getId()));

        return AlternantProfileResponse.from(profile, schedule.size());
    }

    // Décision APP-110 : le rythme AUTRE n'est plus saisissable — il générait
    // un calendrier 1/1 par défaut incohérent. La valeur reste dans l'enum
    // uniquement pour lire les profils historiques déjà en base.
    private void rejeterRythmeAutre(CreateAlternantProfileRequest request) {
        if (request.rythme() == RythmeAlternance.AUTRE) {
            throw new IllegalArgumentException(
                    "Le rythme AUTRE n'est plus disponible : choisissez l'un des rythmes proposés");
        }
    }

    // Champ optionnel pour les anciens clients : à défaut, on reproduit
    // l'ordre historique du générateur (SEMAINE_3_1 → entreprise, sinon école).
    private PremiereSemaine resolvePremiereSemaine(CreateAlternantProfileRequest request) {
        return request.premiereSemaine() != null
                ? request.premiereSemaine()
                : PremiereSemaine.defaultFor(request.rythme());
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
