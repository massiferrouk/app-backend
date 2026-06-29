package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.HideMessageRequest;
import com.studup.backend.model.dto.request.HideReviewRequest;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.dto.response.MessageReportResponse;
import com.studup.backend.model.dto.response.ReviewResponse;
import com.studup.backend.model.enums.ReviewTargetType;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    // ─── GET /api/v1/admin/moderation/messages ───────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithPendingMessageReports() throws Exception {
        MessageReportResponse report = new MessageReportResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "contenu inapproprié", OffsetDateTime.now());

        when(moderationService.getPendingReports(any()))
                .thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/moderation/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].motif").value("contenu inapproprié"));
    }

    @Test
    void shouldReturn401OnMessageQueueWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/moderation/messages"))
                .andExpect(status().isUnauthorized());
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
