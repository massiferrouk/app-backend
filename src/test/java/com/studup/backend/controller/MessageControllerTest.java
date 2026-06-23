package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.model.dto.request.SendMessageRequest;
import com.studup.backend.model.dto.response.MessageResponse;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.MessageService;
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
        SendMessageRequest request = new SendMessageRequest("Bonjour !");
        MessageResponse response = buildResponse();

        when(messageService.sendMessage(eq("alice@studup.fr"), any(UUID.class), eq("Bonjour !")))
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
        SendMessageRequest request = new SendMessageRequest("Bonjour !");

        mockMvc.perform(post("/api/v1/messages/send/" + UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn400WhenContentBlank() throws Exception {
        SendMessageRequest request = new SendMessageRequest("");

        mockMvc.perform(post("/api/v1/messages/send/" + UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
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
}
