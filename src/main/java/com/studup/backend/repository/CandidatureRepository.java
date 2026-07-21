package com.studup.backend.repository;

import com.studup.backend.model.entity.Candidature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidatureRepository extends JpaRepository<Candidature, UUID> {

    // Mes candidatures, la plus récemment modifiée en premier
    List<Candidature> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    // Sert à rendre l'ajout idempotent (contrainte unique en base)
    Optional<Candidature> findByUserIdAndLogementId(UUID userId, UUID logementId);

    /**
     * Nombre d'étudiants DISTINCTS suivant au moins une de ces annonces
     * (APP-119) — KPI « étudiants intéressés » de l'accueil propriétaire.
     * Distinct : un étudiant qui suit 3 annonces du même proprio compte 1.
     */
    @Query("SELECT COUNT(DISTINCT c.user.id) FROM Candidature c WHERE c.logement.id IN :ids")
    long countDistinctUsersByLogementIds(@Param("ids") List<UUID> ids);
}
