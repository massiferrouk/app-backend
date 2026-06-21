package com.studup.backend.repository;

import com.studup.backend.model.entity.Disponibilite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DisponibiliteRepository extends JpaRepository<Disponibilite, UUID> {

    List<Disponibilite> findByLogementIdOrderByDateDebutAsc(UUID logementId);

    // Détecte les chevauchements : deux plages [A,B] et [C,D] se chevauchent si A <= D ET C <= B
    @Query("""
            SELECT COUNT(d) > 0 FROM Disponibilite d
            WHERE d.logement.id = :logementId
            AND d.dateDebut <= :dateFin
            AND d.dateFin >= :dateDebut
            """)
    boolean existsOverlap(
            @Param("logementId") UUID logementId,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin
    );
}
