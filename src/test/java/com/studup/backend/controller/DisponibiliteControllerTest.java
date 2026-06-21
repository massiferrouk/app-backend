package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateDisponibiliteRequest;
import com.studup.backend.model.dto.response.DisponibiliteResponse;
import com.studup.backend.model.enums.DisponibiliteType;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.security.SecurityService;
import com.studup.backend.service.DisponibiliteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DisponibiliteController.class)
class DisponibiliteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private DisponibiliteService disponibiliteService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private SecurityService securityService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private UUID logementId = UUID.randomUUID();

    // ─── POST /api/v1/logements/{id}/disponibilites ───────────────────────────

    @Test
    @WithMockUser(username = "pierre@studup.fr")
    void shouldReturn201WhenDisponibiliteCreated() throws Exception {
        CreateDisponibiliteRequest request = new CreateDisponibiliteRequest(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                DisponibiliteType.LIBRE
        );

        DisponibiliteResponse fakeResponse = new DisponibiliteResponse(
                UUID.randomUUID(), logementId,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                DisponibiliteType.LIBRE
        );

        when(securityService.isLogementOwner(eq(logementId), any())).thenReturn(true);
        when(disponibiliteService.create(eq(logementId), any())).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/v1/logements/{id}/disponibilites", logementId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LIBRE"))
                .andExpect(jsonPath("$.dateDebut").value("2026-07-01"));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenDateDebutIsMissing() throws Exception {
        // dateDebut = null → @NotNull doit rejeter avec 400
        String body = "{\"dateFin\":\"2026-07-31\"}";

        mockMvc.perform(post("/api/v1/logements/{id}/disponibilites", logementId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/v1/logements/{id}/disponibilites ────────────────────────────

    @Test
    @WithMockUser
    void shouldReturn200WithListOfDisponibilites() throws Exception {
        List<DisponibiliteResponse> fakeList = List.of(
                new DisponibiliteResponse(UUID.randomUUID(), logementId,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), DisponibiliteType.LIBRE),
                new DisponibiliteResponse(UUID.randomUUID(), logementId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), DisponibiliteType.BLOQUE)
        );

        when(disponibiliteService.findByLogement(logementId)).thenReturn(fakeList);

        mockMvc.perform(get("/api/v1/logements/{id}/disponibilites", logementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("LIBRE"))
                .andExpect(jsonPath("$[1].type").value("BLOQUE"));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenLogementNotFound() throws Exception {
        when(disponibiliteService.findByLogement(logementId))
                .thenThrow(new ResourceNotFoundException("Logement introuvable"));

        mockMvc.perform(get("/api/v1/logements/{id}/disponibilites", logementId))
                .andExpect(status().isNotFound());
    }
}
