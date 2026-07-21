package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.model.dto.request.SendMessageRequest;
import com.studup.backend.model.dto.response.MessagePhotoResponse;
import com.studup.backend.model.dto.response.MessageResponse;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.MediaMessageService;
import com.studup.backend.service.MessageService;
import com.studup.backend.service.ModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private MediaMessageService mediaMessageService;

    @MockitoBean
    private ModerationService moderationService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private MessageResponse buildResponse() {
        return new MessageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bonjour !",
                false,
                OffsetDateTime.now()
        );
    }

    // ─── POST /send/{receiverId} ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn201WhenMessageSent() throws Exception {
        SendMessageRequest request = new SendMessageRequest("Bonjour !", null);
        MessageResponse response = buildResponse();

        when(messageService.sendMessage(
                eq("alice@studup.fr"), any(UUID.class), eq("Bonjour !"), eq(null)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/messages/send/" + UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Bonjour !"))
                .andExpect(jsonPath("$.isRead").value(false));
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        SendMessageRequest request = new SendMessageRequest("Bonjour !", null);

        mockMvc.perform(post("/api/v1/messages/send/" + UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn400WhenContentBlank() throws Exception {
        SendMessageRequest request = new SendMessageRequest("", null);

        mockMvc.perform(post("/api/v1/messages/send/" + UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /conversations (APP-75) ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithMyConversations() throws Exception {
        var summary = new com.studup.backend.model.dto.response.ConversationSummaryResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Thomas D.",
                "Salut, ton studio est libre en mars ?",
                java.time.OffsetDateTime.now(), 2L,
                UUID.randomUUID(), "Bordeaux", "STUDIO");

        when(messageService.getMesConversations("alice@studup.fr"))
                .thenReturn(java.util.List.of(summary));

        mockMvc.perform(get("/api/v1/messages/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].partnerName").value("Thomas D."))
                .andExpect(jsonPath("$[0].unreadCount").value(2));
    }

    @Test
    void shouldReturn401OnConversationsWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/messages/conversations"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /{conversationId} ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithHistory() throws Exception {
        UUID conversationId = UUID.randomUUID();

        when(messageService.getHistory(eq("alice@studup.fr"), eq(conversationId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildResponse())));

        mockMvc.perform(get("/api/v1/messages/" + conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Bonjour !"));
    }

    // ─── PATCH /{messageId}/read ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "bob@studup.fr")
    void shouldReturn200WhenMarkedAsRead() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageResponse response = new MessageResponse(
                messageId, UUID.randomUUID(), UUID.randomUUID(),
                "Bonjour !", true, OffsetDateTime.now());

        when(messageService.markAsRead(eq("bob@studup.fr"), eq(messageId)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/messages/" + messageId + "/read")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
    }

    // ─── POST /{messageId}/photos ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn201WhenPhotosUploaded() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessagePhotoResponse photoResponse = new MessagePhotoResponse(
                messageId.toString(),
                List.of("https://minio/signed-url-1", "https://minio/signed-url-2"));

        when(mediaMessageService.uploadPhotos(eq(messageId), any()))
                .thenReturn(photoResponse);

        MockMultipartFile photo1 = new MockMultipartFile(
                "photos", "photo1.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile photo2 = new MockMultipartFile(
                "photos", "photo2.jpg", "image/jpeg", new byte[]{4, 5, 6});

        mockMvc.perform(multipart("/api/v1/messages/" + messageId + "/photos")
                        .file(photo1)
                        .file(photo2)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(messageId.toString()))
                .andExpect(jsonPath("$.photoUrls[0]").value("https://minio/signed-url-1"))
                .andExpect(jsonPath("$.photoUrls[1]").value("https://minio/signed-url-2"));
    }

    @Test
    void shouldReturn401WhenUploadingPhotosWithoutAuth() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "photos", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/messages/" + UUID.randomUUID() + "/photos")
                        .file(photo)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
