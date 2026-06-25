package com.studup.backend.service;

import com.studup.backend.event.ReviewCreatedEvent;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.ReputationScoreResponse;
import com.studup.backend.model.entity.Review;
import com.studup.backend.model.entity.ReputationScore;
import com.studup.backend.model.enums.ReviewTargetType;
import com.studup.backend.repository.ReputationScoreRepository;
import com.studup.backend.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

    @Mock private ReputationScoreRepository reputationRepository;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks private ReputationService reputationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    private Review buildReview(int rating, boolean recent) {
        return Review.builder()
                .id(UUID.randomUUID())
                .targetUserId(userId)
                .targetType(ReviewTargetType.USER)
                .rating(rating)
                .isModerated(false)
                .createdAt(recent
                        ? OffsetDateTime.now().minusMonths(1)
                        : OffsetDateTime.now().minusMonths(12))
                .build();
    }

    // ─── shouldCalculateWeightedScore ────────────────────────────────────────

    @Test
    void shouldCalculateWeightedScore() {
        // 1 avis récent (5 étoiles, poids 1.5) + 1 avis ancien (1 étoile, poids 1.0)
        // Moyenne = (5*1.5 + 1*1.0) / (1.5 + 1.0) = 8.5 / 2.5 = 3.40
        List<Review> reviews = List.of(buildReview(5, true), buildReview(1, false));

        when(reviewRepository.findByTargetUserIdAndIsModeratedFalse(eq(userId), any()))
                .thenReturn(new PageImpl<>(reviews));
        when(reputationRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(reputationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReputationScoreResponse response = reputationService.recalculateForUser(userId);

        assertThat(response.avgRating()).isEqualByComparingTo(new BigDecimal("3.40"));
        assertThat(response.totalReviews()).isEqualTo(2);
    }

    // ─── shouldReturnZeroWhenNoReviews ────────────────────────────────────────

    @Test
    void shouldReturnZeroWhenNoReviews() {
        when(reviewRepository.findByTargetUserIdAndIsModeratedFalse(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reputationRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(reputationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReputationScoreResponse response = reputationService.recalculateForUser(userId);

        assertThat(response.avgRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalReviews()).isEqualTo(0);
    }

    // ─── shouldAssignBadgeCorrectly ───────────────────────────────────────────

    @Test
    void shouldAssignNouveauBadgeWhenFewReviews() {
        assertThat(ReputationService.calculateBadge(new BigDecimal("4.8"), 3))
                .isEqualTo("Nouveau");
    }

    @Test
    void shouldAssignFiableBadge() {
        assertThat(ReputationService.calculateBadge(new BigDecimal("4.0"), 10))
                .isEqualTo("Fiable");
    }

    @Test
    void shouldAssignExpertBadge() {
        assertThat(ReputationService.calculateBadge(new BigDecimal("4.5"), 25))
                .isEqualTo("Expert");
    }

    @Test
    void shouldAssignAmbassadeurBadge() {
        assertThat(ReputationService.calculateBadge(new BigDecimal("4.8"), 55))
                .isEqualTo("Ambassadeur");
    }

    // ─── shouldUpdateExistingScore ────────────────────────────────────────────

    @Test
    void shouldUpdateExistingScore() {
        ReputationScore existing = ReputationScore.builder()
                .id(UUID.randomUUID()).userId(userId)
                .avgRating(new BigDecimal("3.00")).totalReviews(5).build();

        when(reviewRepository.findByTargetUserIdAndIsModeratedFalse(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(buildReview(5, true))));
        when(reputationRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(reputationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReputationScoreResponse response = reputationService.recalculateForUser(userId);

        assertThat(response.avgRating()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    // ─── shouldTriggerRecalculateOnReviewCreated ─────────────────────────────

    @Test
    void shouldTriggerRecalculateOnReviewCreated() {
        Review review = buildReview(4, true);

        when(reviewRepository.findByTargetUserIdAndIsModeratedFalse(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(reputationRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(reputationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reputationService.onReviewCreated(new ReviewCreatedEvent(review));

        verify(reputationRepository).save(any());
    }

    // ─── shouldThrowWhenScoreNotFound ─────────────────────────────────────────

    @Test
    void shouldThrowWhenScoreNotFound() {
        when(reputationRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reputationService.getScore(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
