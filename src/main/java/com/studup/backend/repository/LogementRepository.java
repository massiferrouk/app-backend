package com.studup.backend.repository;

import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.VilleAssociee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LogementRepository extends JpaRepository<Logement, UUID>, JpaSpecificationExecutor<Logement> {

    List<Logement> findByOwnerId(UUID ownerId);

    /** Répartition des annonces par statut, en une requête (APP-121). */
    @Query("SELECT l.statut, COUNT(l) FROM Logement l GROUP BY l.statut")
    List<Object[]> countGroupByStatut();

    /** Liste d'administration filtrée par statut (APP-121). */
    Page<Logement> findByStatut(LogementStatut statut, Pageable pageable);
}
