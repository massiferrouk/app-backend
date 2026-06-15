package com.studup.backend.repository;

import com.studup.backend.model.entity.JourFerie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JourFerieRepository extends JpaRepository<JourFerie, UUID> {

    // Charge tous les jours fériés FR entre deux dates pour une plage de calendrier
    Set<JourFerie> findByPaysAndDateJourBetween(String pays, LocalDate debut, LocalDate fin);
}
