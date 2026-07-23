package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.HideMessageRequest;
import com.studup.backend.model.dto.request.HideReviewRequest;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.dto.response.AdminDashboardResponse;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.model.dto.response.MessageReportResponse;
import com.studup.backend.model.dto.response.MotInterditResponse;
import com.studup.backend.model.dto.response.ReviewResponse;
import com.studup.backend.model.enums.ReviewTargetType;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.security.SecurityConfig;
import com.studup.backend.service.AdminService;
import com.studup.backend.service.CalendrierService;
import com.studup.backend.service.DisponibiliteService;
import com.studup.backend.service.MatchingService;
import com.studup.backend.service.ModerationService;
import com.studup.backend.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AdminService adminService;
    @MockitoBean private ReviewService reviewService;
    @MockitoBean private ModerationService moderationService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private DisponibiliteService disponibiliteService;
    @MockitoBean private MatchingService matchingService;
    @MockitoBean private CalendrierService calendrierService;

    private AdminUserResponse fakeUser(UUID id, boolean isActive) {
        return new AdminUserResponse(id, "bob@studup.fr", "Bob", "Dupont",
                UserRole.ALTERNANT, true, isActive, OffsetDateTime.now(), null);
    }

    private ReviewResponse fakeReview(UUID id) {
        return new ReviewResponse(id, UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), ReviewTargetType.USER, 2, "Commentaire problématique",
                false, OffsetDateTime.now());
    }

    // ─── GET /api/v1/admin/users ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithUserList() throws Exception {
        UUID userId = UUID.randomUUID();
        Page<AdminUserResponse> page = new PageImpl<>(
                List.of(fakeUser(userId, true)),
                PageRequest.of(0, 20), 1);

        when(adminService.listUsers(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("bob@studup.fr"));
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ALTERNANT")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /api/v1/admin/dashboard (APP-121) ───────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithDashboard() throws Exception {
        when(adminService.getDashboard()).thenReturn(new AdminDashboardResponse(
                42L, Map.of(UserRole.ALTERNANT, 12L), 3L, 1L, 5L, 20L,
                10L, Map.of(LogementStatut.ACTIF, 8L), 4L, 2L, 7L));

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalComptes").value(42))
                .andExpect(jsonPath("$.comptesParRole.ALTERNANT").value(12))
                .andExpect(jsonPath("$.annoncesParStatut.ACTIF").value(8))
                .andExpect(jsonPath("$.signalementsEnAttente").value(4))
                .andExpect(jsonPath("$.annoncesSignalees").value(2));
    }

    @Test
    void shouldReturn401OnDashboardWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ETUDIANT")
    void shouldReturn403OnDashboardWhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    // ─── PUT /api/v1/admin/users/{id}/suspend ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@studup.fr")
    void shouldReturn200OnSuspend() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.suspendUser(eq(userId), any())).thenReturn(fakeUser(userId, false));

        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@studup.fr")
    void shouldReturn404WhenUserNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(adminService.suspendUser(eq(unknownId), any()))
                .thenThrow(new ResourceNotFoundException("Utilisateur introuvable"));

        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", unknownId))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/v1/admin/users/{id}/ban ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@studup.fr")
    void shouldReturn200OnBan() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.banUser(eq(userId), any())).thenReturn(fakeUser(userId, false));

        mockMvc.perform(put("/api/v1/admin/users/{id}/ban", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    // ─── PUT /api/v1/admin/users/{id}/reactivate (APP-121) ───────────────────

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@studup.fr")
    void shouldReturn200OnReactivate() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.reactivateUser(eq(userId), any())).thenReturn(fakeUser(userId, true));

        mockMvc.perform(put("/api/v1/admin/users/{id}/reactivate", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void shouldReturn401OnReactivateWhenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/{id}/reactivate", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ALTERNANT")
    void shouldReturn403OnReactivateWhenNotAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/{id}/reactivate", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    // ─── GET /api/v1/admin/moderation/messages ───────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithPendingMessageReports() throws Exception {
        MessageReportResponse report = new MessageReportResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "contenu inapproprié", OffsetDateTime.now(),
                "le message signalé", UUID.randomUUID(), "Bob B",
                OffsetDateTime.now(), "Alice A");

        when(moderationService.getPendingReports(any()))
                .thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/moderation/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].motif").value("contenu inapproprié"))
                // Le modérateur doit voir ce qu'il modère (APP-121)
                .andExpect(jsonPath("$.content[0].contenuMessage").value("le message signalé"))
                .andExpect(jsonPath("$.content[0].auteurNom").value("Bob B"))
                .andExpect(jsonPath("$.content[0].signalePar").value("Alice A"))
                // Format de pagination unique de l'API (PageResponse)
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturn401OnMessageQueueWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/moderation/messages"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Modération des annonces (APP-121) ───────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200OnListeLogements() throws Exception {
        when(moderationService.listerLogements(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/admin/logements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200OnSuspensionLogement() throws Exception {
        when(moderationService.suspendreLogement(any(), any()))
                .thenReturn(fakeLogement(LogementStatut.SUSPENDU, "Photos trompeuses"));

        mockMvc.perform(put("/api/v1/admin/logements/{id}/suspendre", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"Photos trompeuses\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SUSPENDU"))
                .andExpect(jsonPath("$.moderationNote").value("Photos trompeuses"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400OnSuspensionSansMotif() throws Exception {
        // Le motif part au propriétaire : le refuser vide est volontaire
        mockMvc.perform(put("/api/v1/admin/logements/{id}/suspendre", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200OnRepublicationLogement() throws Exception {
        when(moderationService.republierLogement(any()))
                .thenReturn(fakeLogement(LogementStatut.ACTIF, null));

        mockMvc.perform(put("/api/v1/admin/logements/{id}/republier", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"));
    }

    @Test
    @WithMockUser(roles = "ALTERNANT")
    void shouldReturn403OnLogementsWhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/logements"))
                .andExpect(status().isForbidden());
    }

    private LogementResponse fakeLogement(LogementStatut statut, String note) {
        return new LogementResponse(
                UUID.randomUUID(), UUID.randomUUID(), "1 rue X", "Paris", "75001",
                null, null, LogementType.STUDIO, new BigDecimal("25"), 1,
                new BigDecimal("700"), BigDecimal.ZERO, "desc", new String[]{},
                statut, false, true, null, List.of(), OffsetDateTime.now(),
                "Bob", note);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithAnnoncesSignalees() throws Exception {
        when(moderationService.getPendingLogementReports(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/admin/moderation/logements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(roles = "ETUDIANT")
    void shouldReturn403OnAnnoncesSignaleesWhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/moderation/logements"))
                .andExpect(status().isForbidden());
    }

    // ─── Mots interdits (APP-121) ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithMotsInterdits() throws Exception {
        when(moderationService.listerMotsInterdits()).thenReturn(List.of(
                new MotInterditResponse(UUID.randomUUID(), "arnaque", OffsetDateTime.now())));

        mockMvc.perform(get("/api/v1/admin/moderation/mots-interdits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mot").value("arnaque"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn201OnAjoutMotInterdit() throws Exception {
        when(moderationService.ajouterMotInterdit(any())).thenReturn(
                new MotInterditResponse(UUID.randomUUID(), "spam", OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/admin/moderation/mots-interdits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mot\":\"spam\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mot").value("spam"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400OnMotVide() throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation/mots-interdits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mot\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn409OnMotDejaPresent() throws Exception {
        when(moderationService.ajouterMotInterdit(any()))
                .thenThrow(new IllegalStateException("Ce mot est déjà dans la liste"));

        mockMvc.perform(post("/api/v1/admin/moderation/mots-interdits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mot\":\"spam\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn204OnSuppressionMotInterdit() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/moderation/mots-interdits/{id}",
                        UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ALTERNANT")
    void shouldReturn403OnMotsInterditsWhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/moderation/mots-interdits"))
                .andExpect(status().isForbidden());
    }

    // ─── PUT /api/v1/admin/moderation/messages/{messageId}/hide ──────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn204OnHideMessage() throws Exception {
        UUID messageId = UUID.randomUUID();
        doNothing().when(moderationService).hideMessage(eq(messageId), any());

        String body = objectMapper.writeValueAsString(
                new HideMessageRequest("Insulte envers un autre utilisateur"));

        mockMvc.perform(put("/api/v1/admin/moderation/messages/{messageId}/hide", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404OnHideUnknownMessage() throws Exception {
        UUID unknownId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Message introuvable"))
                .when(moderationService).hideMessage(eq(unknownId), any());

        String body = objectMapper.writeValueAsString(
                new HideMessageRequest("Note de modération"));

        mockMvc.perform(put("/api/v1/admin/moderation/messages/{messageId}/hide", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/v1/admin/moderation/reviews ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithReportedReviews() throws Exception {
        UUID reviewId = UUID.randomUUID();
        Page<ReviewResponse> page = new PageImpl<>(
                List.of(fakeReview(reviewId)),
                PageRequest.of(0, 20), 1);

        when(reviewService.getReportedReviews(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/moderation/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void shouldReturn401OnModerationQueueWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/moderation/reviews"))
                .andExpect(status().isUnauthorized());
    }

    // ─── PUT /api/v1/admin/moderation/reviews/{id}/hide ──────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn204OnHideReview() throws Exception {
        UUID reviewId = UUID.randomUUID();
        doNothing().when(reviewService).hideReview(eq(reviewId), any());

        String body = objectMapper.writeValueAsString(
                new HideReviewRequest("Contenu offensant détecté"));

        mockMvc.perform(put("/api/v1/admin/moderation/reviews/{id}/hide", reviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404OnHideUnknownReview() throws Exception {
        UUID unknownId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Avis introuvable"))
                .when(reviewService).hideReview(eq(unknownId), any());

        String body = objectMapper.writeValueAsString(
                new HideReviewRequest("Contenu offensant"));

        mockMvc.perform(put("/api/v1/admin/moderation/reviews/{id}/hide", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
