package com.studup.backend.controller;

import com.studup.backend.model.dto.request.HideReviewRequest;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.dto.response.ReviewResponse;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.service.AdminService;
import com.studup.backend.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final ReviewService reviewService;

    public AdminController(AdminService adminService, ReviewService reviewService) {
        this.adminService = adminService;
        this.reviewService = reviewService;
    }

    // Liste des utilisateurs avec filtres optionnels
    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(adminService.listUsers(role, isActive, pageable));
    }

    // Suspension : compte désactivé, tokens révoqués immédiatement
    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<AdminUserResponse> suspendUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails) {
        return ResponseEntity.ok(adminService.suspendUser(id, adminDetails.getUsername()));
    }

    // Bannissement : suspension permanente avec soft delete
    @PutMapping("/users/{id}/ban")
    public ResponseEntity<AdminUserResponse> banUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails) {
        return ResponseEntity.ok(adminService.banUser(id, adminDetails.getUsername()));
    }

    // Queue de modération : avis signalés en attente de décision
    @GetMapping("/moderation/reviews")
    public ResponseEntity<Page<ReviewResponse>> getReportedReviews(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReportedReviews(pageable));
    }

    // Masquer un avis signalé et notifier son auteur
    @PutMapping("/moderation/reviews/{id}/hide")
    public ResponseEntity<Void> hideReview(
            @PathVariable UUID id,
            @Valid @RequestBody HideReviewRequest request) {
        reviewService.hideReview(id, request.moderationNote());
        return ResponseEntity.noContent().build();
    }
}
