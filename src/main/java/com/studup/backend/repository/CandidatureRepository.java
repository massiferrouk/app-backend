package com.studup.backend.repository;

import com.studup.backend.model.entity.Candidature;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
