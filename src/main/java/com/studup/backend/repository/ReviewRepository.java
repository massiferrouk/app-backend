package com.studup.backend.repository;

import com.studup.backend.model.entity.Review;
import com.studup.backend.model.enums.ReviewTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    // Tous les avis reçus par un utilisateur (non modérés)
    Page<Review> findByTargetUserIdAndIsModeratedFalse(UUID targetUserId, Pageable pageable);

    // Vérifie qu'un auteur n'a pas déjà laissé un avis USER sur cet accord
    Optional<Review> findByAuthorIdAndAccordIdAndTargetType(
            UUID authorId, UUID accordId, ReviewTargetType targetType);

    // Vérifie l'unicité auteur + accord + cible utilisateur
    Optional<Review> findByAuthorIdAndAccordIdAndTargetUserId(
            UUID authorId, UUID accordId, UUID targetUserId);

    // Vérifie l'unicité auteur + accord + cible logement
    Optional<Review> findByAuthorIdAndAccordIdAndTargetLogementId(
            UUID authorId, UUID accordId, UUID targetLogementId);

    // Queue de modération admin : signalés mais pas encore masqués
    Page<Review> findByIsReportedTrueAndIsModeratedFalse(Pageable pageable);
}
