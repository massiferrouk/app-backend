package com.studup.backend.service;

import com.studup.backend.event.ReviewCreatedEvent;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.ReputationScoreResponse;
import com.studup.backend.model.entity.Review;
import com.studup.backend.model.entity.ReputationScore;
import com.studup.backend.model.enums.ReviewTargetType;
import com.studup.backend.repository.ReviewRepository;
import com.studup.backend.repository.ReputationScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReputationService {

    private static final Logger log = LoggerFactory.getLogger(ReputationService.class);

    // Les avis des 6 derniers mois pèsent 1.5x plus
    private static final double POIDS_RECENT = 1.5;
    private static final int MOIS_RECENTS = 6;

    private final ReputationScoreRepository reputationRepository;
    private final ReviewRepository reviewRepository;

    public ReputationService(ReputationScoreRepository reputationRepository,
                             ReviewRepository reviewRepository) {
        this.reputationRepository = reputationRepository;
        this.reviewRepository = reviewRepository;
    }

    /**
     * Déclenché automatiquement après chaque nouvel avis.
     * Recalcule le score de la cible concernée.
     */
    @EventListener
    @Transactional
    public void onReviewCreated(ReviewCreatedEvent event) {
        Review review = event.review();
        if (review.getTargetUserId() != null) {
            recalculateForUser(review.getTargetUserId());
        }
    }

    /**
     * Recalcule le score de réputation d'un utilisateur.
     * Appelé par l'EventListener et par le job nightly.
     */
    @Transactional
    public ReputationScoreResponse recalculateForUser(UUID userId) {
        List<Review> allReviews = reviewRepository
                .findByTargetUserIdAndIsModeratedFalse(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();

        BigDecimal avgRating = calculateWeightedAverage(allReviews);

        List<Review> logementReviews = allReviews.stream()
                .filter(r -> r.getTargetType() == ReviewTargetType.LOGEMENT)
                .toList();
        BigDecimal logementScore = calculateWeightedAverage(logementReviews);

        ReputationScore score = reputationRepository.findByUserId(userId)
                .orElse(ReputationScore.builder().userId(userId).build());

        score.setAvgRating(avgRating);
        score.setTotalReviews(allReviews.size());
        score.setLogementScore(logementScore);

        ReputationScore saved = reputationRepository.save(score);

        log.info("Score réputation recalculé — userId={} avgRating={} totalReviews={}",
                userId, avgRating, allReviews.size());

        return ReputationScoreResponse.from(saved);
    }

    /**
     * Retourne le score de réputation d'un utilisateur.
     */
    @Transactional(readOnly = true)
    public ReputationScoreResponse getScore(UUID userId) {
        // Un utilisateur sans aucun avis n'a pas encore de ligne reputation_scores :
        // on renvoie un score « vierge » (0 avis) plutôt qu'un 404, sinon le détail
        // d'un logement d'un nouveau propriétaire casse côté client.
        ReputationScore score = reputationRepository.findByUserId(userId)
                .orElse(ReputationScore.builder().userId(userId).build());
        return ReputationScoreResponse.from(score);
    }

    /**
     * Recalcule tous les scores — appelé par le job nightly.
     */
    @Transactional
    public void recalculateAll() {
        List<UUID> userIds = reputationRepository.findAllUserIdsWithReviews();
        log.info("Recalcul nightly réputation — {} utilisateurs", userIds.size());
        userIds.forEach(this::recalculateForUser);
    }

    // ─── Calcul de la moyenne pondérée ───────────────────────────────────────

    private BigDecimal calculateWeightedAverage(List<Review> reviews) {
        if (reviews.isEmpty()) return BigDecimal.ZERO;

        OffsetDateTime seuilRecent = OffsetDateTime.now().minusMonths(MOIS_RECENTS);

        double sumPondere = 0;
        double sumPoids = 0;

        for (Review r : reviews) {
            double poids = r.getCreatedAt().isAfter(seuilRecent) ? POIDS_RECENT : 1.0;
            sumPondere += r.getRating() * poids;
            sumPoids += poids;
        }

        return BigDecimal.valueOf(sumPondere / sumPoids)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ─── Calcul du badge selon le score et le nombre d'avis ──────────────────

    public static String calculateBadge(BigDecimal avgRating, int totalReviews) {
        if (totalReviews < 5) return "Nouveau";
        if (totalReviews >= 50 && avgRating.compareTo(BigDecimal.valueOf(4.5)) >= 0) return "Ambassadeur";
        if (totalReviews >= 20 && avgRating.compareTo(BigDecimal.valueOf(4.2)) >= 0) return "Expert";
        if (avgRating.compareTo(BigDecimal.valueOf(3.5)) >= 0) return "Fiable";
        return "Nouveau";
    }
}
