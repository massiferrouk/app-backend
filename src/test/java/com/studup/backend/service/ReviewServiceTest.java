package com.studup.backend.service;

import com.studup.backend.event.ReviewCreatedEvent;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.ReviewRequest;
import com.studup.backend.model.dto.response.ReviewResponse;
import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.Review;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.ReviewTargetType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.ReviewRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private AccordRepository accordRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ReviewService reviewService;

    private User alice;
    private User bob;
    private Accord accord;

    @BeforeEach
    void setUp() {
        alice = User.builder()
                .id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        bob = User.builder()
                .id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("B").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        accord = Accord.builder()
                .id(UUID.randomUUID())
                .initiatorId(alice.getId())
                .receiverId(bob.getId())
                .type(AccordType.ECHANGE_TOTAL)
                .statut(AccordStatut.TERMINE)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // ─── shouldCreateReviewSuccessfully ──────────────────────────────────────

    @Test
    void shouldCreateReviewSuccessfully() {
        ReviewRequest request = new ReviewRequest(
                accord.getId(), ReviewTargetType.USER, bob.getId(), null, 5, "Échange parfait");

        Review saved = Review.builder()
                .id(UUID.randomUUID()).authorId(alice.getId())
                .accordId(accord.getId()).targetType(ReviewTargetType.USER)
                .targetUserId(bob.getId()).rating(5).comment("Échange parfait")
                .isModerated(false).createdAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findById(accord.getId())).thenReturn(Optional.of(accord));
        when(reviewRepository.findByAuthorIdAndAccordIdAndTargetUserId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any())).thenReturn(saved);

        ReviewResponse response = reviewService.createReview("alice@studup.fr", request);

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.targetType()).isEqualTo(ReviewTargetType.USER);
        assertThat(response.isModerated()).isFalse();
    }

    // ─── shouldRejectReviewOnNonTerminatedAccord ──────────────────────────────

    @Test
    void shouldRejectReviewOnNonTerminatedAccord() {
        accord.setStatut(AccordStatut.EN_COURS);
        ReviewRequest request = new ReviewRequest(
                accord.getId(), ReviewTargetType.USER, bob.getId(), null, 4, null);

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findById(accord.getId())).thenReturn(Optional.of(accord));

        assertThatThrownBy(() -> reviewService.createReview("alice@studup.fr", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminés");
    }

    // ─── shouldRejectReviewWhenNotParticipant ─────────────────────────────────

    @Test
    void shouldRejectReviewWhenNotParticipant() {
        User charlie = User.builder().id(UUID.randomUUID()).email("charlie@studup.fr")
                .firstName("C").lastName("C").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        ReviewRequest request = new ReviewRequest(
                accord.getId(), ReviewTargetType.USER, bob.getId(), null, 3, null);

        when(userRepository.findByEmail("charlie@studup.fr")).thenReturn(Optional.of(charlie));
        when(accordRepository.findById(accord.getId())).thenReturn(Optional.of(accord));

        assertThatThrownBy(() -> reviewService.createReview("charlie@studup.fr", request))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─── shouldRejectDuplicateReview ─────────────────────────────────────────

    @Test
    void shouldRejectDuplicateReview() {
        ReviewRequest request = new ReviewRequest(
                accord.getId(), ReviewTargetType.USER, bob.getId(), null, 4, null);

        Review existing = Review.builder().id(UUID.randomUUID()).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findById(accord.getId())).thenReturn(Optional.of(accord));
        when(reviewRepository.findByAuthorIdAndAccordIdAndTargetUserId(
                alice.getId(), accord.getId(), bob.getId()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> reviewService.createReview("alice@studup.fr", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà laissé un avis");
    }

    // ─── shouldRejectWhenTargetUserIdMissingForUserReview ────────────────────

    @Test
    void shouldRejectWhenTargetUserIdMissingForUserReview() {
        ReviewRequest request = new ReviewRequest(
                accord.getId(), ReviewTargetType.USER, null, null, 4, null);

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findById(accord.getId())).thenReturn(Optional.of(accord));

        assertThatThrownBy(() -> reviewService.createReview("alice@studup.fr", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetUserId");
    }

    // ─── shouldReturnPagedReviews ─────────────────────────────────────────────

    @Test
    void shouldReturnPagedReviews() {
        Review review = Review.builder()
                .id(UUID.randomUUID()).authorId(alice.getId())
                .targetUserId(bob.getId()).accordId(accord.getId())
                .targetType(ReviewTargetType.USER).rating(4)
                .isModerated(false).createdAt(OffsetDateTime.now()).build();

        when(reviewRepository.findByTargetUserIdAndIsModeratedFalse(
                eq(bob.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(review)));

        var page = reviewService.getReviewsForUser(bob.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).rating()).isEqualTo(4);
    }

    // ─── shouldReportReviewWithoutError ──────────────────────────────────────

    @Test
    void shouldReportReviewWithoutError() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.builder().id(reviewId).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        reviewService.reportReview("alice@studup.fr", reviewId);

        verify(reviewRepository).findById(reviewId);
    }
}
