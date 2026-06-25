package com.studup.backend.repository;

import com.studup.backend.model.entity.ReputationScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReputationScoreRepository extends JpaRepository<ReputationScore, UUID> {

    Optional<ReputationScore> findByUserId(UUID userId);

    // Retourne tous les userIds distincts ayant reçu au moins un avis — pour le job nightly
    @Query("SELECT DISTINCT r.targetUserId FROM Review r WHERE r.targetUserId IS NOT NULL")
    List<UUID> findAllUserIdsWithReviews();
}
