package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.model.dto.response.NotificationResponse;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationController.class)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private NotificationResponse buildResponse() {
        return new NotificationResponse(
                UUID.randomUUID(), NotificationType.NOUVEAU_MESSAGE,
                "Nouveau message", "Bob vous a écrit",
                false, "messages/123", OffsetDateTime.now());
    }

    // ─── GET /notifications ───────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithNotifications() throws Exception {
        when(notificationService.getMyNotifications(eq("alice@studup.fr"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildResponse())));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("NOUVEAU_MESSAGE"));
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /notifications/unread-count ─────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithUnreadCount() throws Exception {
        when(notificationService.countUnread("alice@studup.fr")).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(5));
    }

    // ─── PATCH /notifications/{id}/read ──────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WhenMarkedAsRead() throws Exception {
        UUID notifId = UUID.randomUUID();
        NotificationResponse response = new NotificationResponse(
                notifId, NotificationType.NOUVEAU_MESSAGE,
                "Nouveau message", "Corps", true, null, OffsetDateTime.now());

        when(notificationService.markAsRead("alice@studup.fr", notifId)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/notifications/" + notifId + "/read").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
    }

    // ─── PATCH /notifications/read-all ───────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WhenAllMarkedAsRead() throws Exception {
        when(notificationService.markAllAsRead("alice@studup.fr")).thenReturn(7);

        mockMvc.perform(patch("/api/v1/notifications/read-all").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedAsRead").value(7));
    }

    // ─── PUT /notifications/fcm-token ────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn204WhenFcmTokenUpdated() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/fcm-token")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fcmToken", "new-token-xyz"))))
                .andExpect(status().isNoContent());
    }
}
