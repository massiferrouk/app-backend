package com.studup.backend.repository;

import com.studup.backend.model.entity.AlternanceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlternanceScheduleRepository extends JpaRepository<AlternanceSchedule, UUID> {

    // Récupère toutes les semaines d'un profil, triées chronologiquement
    List<AlternanceSchedule> findByProfileIdOrderBySemaineAsc(UUID profileId);

    // Supprime toutes les semaines d'un profil (utilisé lors du recalcul du calendrier)
    void deleteByProfileId(UUID profileId);

    // Recherche une semaine précise pour un profil donné (override)
    Optional<AlternanceSchedule> findByProfileIdAndSemaine(UUID profileId, LocalDate semaine);
}
