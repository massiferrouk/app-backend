package com.studup.backend.controller;

import com.studup.backend.model.dto.request.HideMessageRequest;
import com.studup.backend.model.dto.request.HideReviewRequest;
import com.studup.backend.model.dto.request.MotInterditRequest;
import com.studup.backend.model.dto.request.SuspendreLogementRequest;
import com.studup.backend.model.dto.response.AdminDashboardResponse;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.dto.response.MessageReportResponse;
import com.studup.backend.model.dto.response.LogementReportResponse;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.model.dto.response.MotInterditResponse;
import com.studup.backend.model.dto.response.PageResponse;
import com.studup.backend.model.dto.response.ReviewResponse;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.service.AdminService;
import com.studup.backend.service.ModerationService;
import com.studup.backend.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final ReviewService reviewService;
    private final ModerationService moderationService;

    public AdminController(AdminService adminService,
                           ReviewService reviewService,
                           ModerationService moderationService) {
        this.adminService = adminService;
        this.reviewService = reviewService;
        this.moderationService = moderationService;
    }

    // Tableau de bord : uniquement des chiffres réellement calculables (APP-121)
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
    }

    // Liste des utilisateurs avec filtres optionnels
    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
                PageResponse.from(adminService.listUsers(role, isActive, pageable)));
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

    // Réactivation : lève la suspension ou le bannissement (APP-121)
    @PutMapping("/users/{id}/reactivate")
    public ResponseEntity<AdminUserResponse> reactivateUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails adminDetails) {
        return ResponseEntity.ok(adminService.reactivateUser(id, adminDetails.getUsername()));
    }

    // Queue de modération messages : signalements en attente de décision
    @GetMapping("/moderation/messages")
    public ResponseEntity<PageResponse<MessageReportResponse>> getPendingMessageReports(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                PageResponse.from(moderationService.getPendingReports(pageable)));
    }

    // Masquer un message signalé avec une note de modération
    @PutMapping("/moderation/messages/{messageId}/hide")
    public ResponseEntity<Void> hideMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody HideMessageRequest request) {
        moderationService.hideMessage(messageId, request.moderationNote());
        return ResponseEntity.noContent().build();
    }

    // ─── Modération des annonces (APP-121) ───────────────────────────────────

    // Liste d'administration : contrairement à la recherche publique, elle
    // montre aussi les brouillons et les annonces déjà suspendues.
    @GetMapping("/logements")
    public ResponseEntity<PageResponse<LogementResponse>> listerLogements(
            @RequestParam(required = false) LogementStatut statut,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
                PageResponse.from(moderationService.listerLogements(statut, pageable)));
    }

    // File des annonces signalées par les utilisateurs (APP-121)
    @GetMapping("/moderation/logements")
    public ResponseEntity<PageResponse<LogementReportResponse>> getPendingLogementReports(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                PageResponse.from(moderationService.getPendingLogementReports(pageable)));
    }

    // Retire l'annonce de la plateforme et prévient son propriétaire du motif
    @PutMapping("/logements/{id}/suspendre")
    public ResponseEntity<LogementResponse> suspendreLogement(
            @PathVariable UUID id,
            @Valid @RequestBody SuspendreLogementRequest request) {
        return ResponseEntity.ok(
                moderationService.suspendreLogement(id, request.motif()));
    }

    @PutMapping("/logements/{id}/republier")
    public ResponseEntity<LogementResponse> republierLogement(@PathVariable UUID id) {
        return ResponseEntity.ok(moderationService.republierLogement(id));
    }

    // ─── Mots interdits (APP-121) ────────────────────────────────────────────
    // La table existait depuis V21 « configurable par l'admin », sans qu'aucun
    // endpoint ne permette de la modifier.

    @GetMapping("/moderation/mots-interdits")
    public ResponseEntity<List<MotInterditResponse>> listerMotsInterdits() {
        return ResponseEntity.ok(moderationService.listerMotsInterdits());
    }

    @PostMapping("/moderation/mots-interdits")
    public ResponseEntity<MotInterditResponse> ajouterMotInterdit(
            @Valid @RequestBody MotInterditRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(moderationService.ajouterMotInterdit(request.mot()));
    }

    @DeleteMapping("/moderation/mots-interdits/{id}")
    public ResponseEntity<Void> supprimerMotInterdit(@PathVariable UUID id) {
        moderationService.supprimerMotInterdit(id);
        return ResponseEntity.noContent().build();
    }

    // Queue de modération : avis signalés en attente de décision
    @GetMapping("/moderation/reviews")
    public ResponseEntity<PageResponse<ReviewResponse>> getReportedReviews(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                PageResponse.from(reviewService.getReportedReviews(pageable)));
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
