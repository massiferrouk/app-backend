package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.model.dto.request.AccordRequest;
import com.studup.backend.model.dto.response.AccordResponse;
import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.AccordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccordController.class)
class AccordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccordService accordService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private AccordResponse accordResponse(AccordStatut statut) {
        return new AccordResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, AccordType.ECHANGE_TOTAL, statut,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(6),
                null, "Bonjour", OffsetDateTime.now(), OffsetDateTime.now(),
                "Alice", "Bob"
        );
    }

    // ─── POST /accords ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn201WhenAccordCreated() throws Exception {
        AccordRequest request = new AccordRequest(
                UUID.randomUUID(), AccordType.ECHANGE_TOTAL,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(6),
                null, null, null, "Bonjour"
        );

        when(accordService.createAccord(eq("alice@studup.fr"), any()))
                .thenReturn(accordResponse(AccordStatut.EN_ATTENTE));

        mockMvc.perform(post("/api/v1/accords")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        AccordRequest request = new AccordRequest(
                UUID.randomUUID(), AccordType.ECHANGE_TOTAL,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(6),
                null, null, null, null
        );

        mockMvc.perform(post("/api/v1/accords")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /accords/mes-accords ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithMesAccords() throws Exception {
        when(accordService.getMesAccords(eq("alice@studup.fr"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(accordResponse(AccordStatut.EN_ATTENTE))));

        mockMvc.perform(get("/api/v1/accords/mes-accords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].statut").value("EN_ATTENTE"));
    }

    // ─── PUT /accords/{id}/accept ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "bob@studup.fr")
    void shouldReturn200WhenAccordAccepted() throws Exception {
        UUID accordId = UUID.randomUUID();

        when(accordService.acceptAccord(eq(accordId), eq("bob@studup.fr")))
                .thenReturn(accordResponse(AccordStatut.ACCEPTE));

        mockMvc.perform(put("/api/v1/accords/{id}/accept", accordId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTE"));
    }

    // ─── PUT /accords/{id}/refuse ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "bob@studup.fr")
    void shouldReturn200WhenAccordRefused() throws Exception {
        UUID accordId = UUID.randomUUID();

        when(accordService.refuseAccord(eq(accordId), eq("bob@studup.fr")))
                .thenReturn(accordResponse(AccordStatut.REFUSE));

        mockMvc.perform(put("/api/v1/accords/{id}/refuse", accordId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSE"));
    }

    // ─── PUT /accords/{id}/cancel ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WhenAccordCancelled() throws Exception {
        UUID accordId = UUID.randomUUID();

        when(accordService.cancelAccord(eq(accordId), eq("alice@studup.fr")))
                .thenReturn(accordResponse(AccordStatut.ANNULE));

        mockMvc.perform(put("/api/v1/accords/{id}/cancel", accordId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULE"));
    }
}
