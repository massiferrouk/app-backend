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
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.model.enums.ReviewTargetType;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.ReviewRepository;
import com.studup.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final AccordRepository accordRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    public ReviewService(ReviewRepository reviewRepository,
                         AccordRepository accordRepository,
                         UserRepository userRepository,
                         ApplicationEventPublisher eventPublisher,
                         NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.accordRepository = accordRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
    }

    /**
     * Crée un avis sur un utilisateur ou un logement après la fin d'un accord.
     * Règles :
     * - L'accord doit être en statut TERMINE
     * - L'auteur doit être l'initiateur ou le destinataire de l'accord
     * - Un seul avis par auteur + accord + cible
     */
    @Transactional
    public ReviewResponse createReview(String userEmail, ReviewRequest request) {
        User author = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Accord accord = accordRepository.findById(request.accordId())
                .orElseThrow(() -> new ResourceNotFoundException("Accord introuvable"));

        // Vérification que l'auteur fait partie de l'accord
        boolean isParticipant = accord.getInitiatorId().equals(author.getId())
                || accord.getReceiverId().equals(author.getId());
        if (!isParticipant) {
            throw new UnauthorizedException("Vous ne pouvez laisser un avis que sur vos propres accords");
        }

        // L'accord doit être terminé pour pouvoir laisser un avis
        if (accord.getStatut() != AccordStatut.TERMINE) {
            throw new IllegalStateException(
                    "Les avis ne peuvent être déposés que sur les accords terminés");
        }

        // Validation de la cohérence targetType / targetId
        validateTargetConsistency(request);

        // Vérification unicité selon la cible
        checkNoDuplicateReview(author.getId(), request);

        Review review = Review.builder()
                .authorId(author.getId())
                .accordId(request.accordId())
                .targetType(request.targetType())
                .targetUserId(request.targetUserId())
                .targetLogementId(request.targetLogementId())
                .rating(request.rating())
                .comment(request.comment())
                .build();

        Review saved = reviewRepository.save(review);

        // Publie un événement pour déclencher le recalcul du score de réputation
        eventPublisher.publishEvent(new ReviewCreatedEvent(saved));

        log.info("Avis créé — authorId={} accordId={} targetType={} rating={}",
                author.getId(), request.accordId(), request.targetType(), request.rating());

        return ReviewResponse.from(saved);
    }

    /**
     * Retourne les avis visibles reçus par un utilisateur, paginés.
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsForUser(UUID targetUserId, Pageable pageable) {
        return reviewRepository
                .findByTargetUserIdAndIsModeratedFalse(targetUserId, pageable)
                .map(ReviewResponse::from);
    }

    /**
     * Signale un avis comme inapproprié.
     * N'importe quel utilisateur authentifié peut signaler.
     * Marque isReported = true — la décision de masquer revient à l'admin.
     */
    @Transactional
    public void reportReview(String userEmail, UUID reviewId) {
        userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Avis introuvable"));

        review.setIsReported(true);
        reviewRepository.save(review);

        log.info("Avis signalé — reviewId={}", reviewId);
    }

    /**
     * Retourne la queue de modération : avis signalés mais pas encore masqués.
     * Réservé aux admins (contrôle fait dans le controller via @PreAuthorize).
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReportedReviews(Pageable pageable) {
        return reviewRepository
                .findByIsReportedTrueAndIsModeratedFalse(pageable)
                .map(ReviewResponse::from);
    }

    /**
     * Masque un avis signalé.
     * Envoie une notification SYSTEME à l'auteur de l'avis.
     * Réservé aux admins.
     */
    @Transactional
    public void hideReview(UUID reviewId, String moderationNote) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Avis introuvable"));

        review.setIsModerated(true);
        review.setModerationNote(moderationNote);
        reviewRepository.save(review);

        // Notifie l'auteur que son avis a été masqué
        notificationService.notify(
                review.getAuthorId(),
                NotificationType.SYSTEME,
                Map.of(),
                null
        );

        log.info("Avis masqué par admin — reviewId={} note={}", reviewId, moderationNote);
    }

    // ─── Méthodes privées ─────────────────────────────────────────────────────

    private void validateTargetConsistency(ReviewRequest request) {
        if (request.targetType() == ReviewTargetType.USER && request.targetUserId() == null) {
            throw new IllegalArgumentException(
                    "targetUserId est obligatoire pour un avis de type USER");
        }
        if (request.targetType() == ReviewTargetType.LOGEMENT && request.targetLogementId() == null) {
            throw new IllegalArgumentException(
                    "targetLogementId est obligatoire pour un avis de type LOGEMENT");
        }
    }

    private void checkNoDuplicateReview(UUID authorId, ReviewRequest request) {
        if (request.targetType() == ReviewTargetType.USER) {
            reviewRepository.findByAuthorIdAndAccordIdAndTargetUserId(
                    authorId, request.accordId(), request.targetUserId())
                    .ifPresent(r -> {
                        throw new IllegalStateException(
                                "Vous avez déjà laissé un avis sur cet utilisateur pour cet accord");
                    });
        } else {
            reviewRepository.findByAuthorIdAndAccordIdAndTargetLogementId(
                    authorId, request.accordId(), request.targetLogementId())
                    .ifPresent(r -> {
                        throw new IllegalStateException(
                                "Vous avez déjà laissé un avis sur ce logement pour cet accord");
                    });
        }
    }
}
