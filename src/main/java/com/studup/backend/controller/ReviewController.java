package com.studup.backend.controller;

import com.studup.backend.model.dto.request.ReviewRequest;
import com.studup.backend.model.dto.response.ReviewResponse;
import com.studup.backend.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // Crée un avis sur un utilisateur ou un logement après un accord terminé
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReview(userDetails.getUsername(), request));
    }

    // Retourne les avis visibles reçus par un utilisateur
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<ReviewResponse>> getReviewsForUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviewsForUser(userId, pageable));
    }

    // Signale un avis comme inapproprié
    @PostMapping("/{id}/report")
    public ResponseEntity<Void> reportReview(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        reviewService.reportReview(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
