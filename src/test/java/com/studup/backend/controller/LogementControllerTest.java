package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.AssocierVilleRequest;
import com.studup.backend.model.dto.request.CreateLogementRequest;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.model.dto.response.PageResponse;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.model.enums.VilleAssociee;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.security.SecurityService;
import com.studup.backend.service.LogementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LogementController.class)
class LogementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LogementService logementService;

    // Requis par SecurityConfig et JwtAuthFilter
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // Requis par JwtAuthFilter (dépend de JwtBlacklistService depuis APP-52)
    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    // Requis par @PreAuthorize("@securityService.isLogementOwner(...)")
    @MockitoBean
    private SecurityService securityService;

    // Logement de référence réutilisé dans les tests
    private LogementResponse fakeResponse() {
        return new LogementResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "12 rue de la Paix",
                "Paris",
                "75001",
                new BigDecimal("48.8698"),
                new BigDecimal("2.3310"),
                LogementType.STUDIO,
                new BigDecimal("25.00"),
                1,
                new BigDecimal("800.00"),
                new BigDecimal("50.00"),
                "Beau studio",
                null,
                LogementStatut.BROUILLON,
                false,
                true,
                null,
                List.of(),
                OffsetDateTime.now(),
                "Pierre"
        );
    }

    // ─── GET /api/v1/logements ────────────────────────────────────────────────

    @Test
    @WithMockUser
    void shouldReturn200WithSearchResults() throws Exception {
        PageResponse<LogementResponse> fakePage = new PageResponse<>(
                List.of(fakeResponse()), 0, 20, 1L, false);

        when(logementService.search(any(), any())).thenReturn(fakePage);

        mockMvc.perform(get("/api/v1/logements")
                        .param("ville", "Paris")
                        .param("loyer_max", "900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].ville").value("Paris"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @WithMockUser
    void shouldReturn200WithEmptyResultsWhenNothingMatches() throws Exception {
        PageResponse<LogementResponse> emptyPage = new PageResponse<>(
                List.of(), 0, 20, 0L, false);

        when(logementService.search(any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/logements")
                        .param("ville", "Inconnu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ─── GET /api/v1/logements/mes-logements (APP-70) ────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithMyLogementsIncludingDrafts() throws Exception {
        when(logementService.getMesLogements("alice@studup.fr"))
                .thenReturn(List.of(fakeResponse()));

        mockMvc.perform(get("/api/v1/logements/mes-logements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].statut").value("BROUILLON"));
    }

    @Test
    void shouldReturn401OnMyLogementsWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/logements/mes-logements"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/v1/logements ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "pierre@studup.fr")
    void shouldReturn201WhenCreateLogementIsValid() throws Exception {
        CreateLogementRequest request = new CreateLogementRequest(
                "12 rue de la Paix", "Paris", "75001",
                LogementType.STUDIO, new BigDecimal("25.00"), 1,
                new BigDecimal("800.00"), new BigDecimal("50.00"),
                "Beau studio", null, true
        );

        when(logementService.createLogement(eq("pierre@studup.fr"), any())).thenReturn(fakeResponse());

        mockMvc.perform(post("/api/v1/logements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ville").value("Paris"))
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    @WithMockUser(username = "pierre@studup.fr")
    void shouldReturn400WhenAdresseIsMissing() throws Exception {
        // adresse est @NotBlank — le controller doit rejeter avec 400 avant d'appeler le service
        CreateLogementRequest request = new CreateLogementRequest(
                "", "Paris", "75001",
                LogementType.STUDIO, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/logements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "pierre@studup.fr")
    void shouldReturn400WhenCodePostalIsInvalid() throws Exception {
        // codePostal doit matcher \d{5} — "7500A" est invalide
        CreateLogementRequest request = new CreateLogementRequest(
                "12 rue de la Paix", "Paris", "7500A",
                LogementType.STUDIO, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/logements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        // Sans @WithMockUser — pas d'authentification → 401 Unauthorized
        // .with(csrf()) est nécessaire pour passer la vérification CSRF avant celle de l'auth
        CreateLogementRequest request = new CreateLogementRequest(
                "12 rue de la Paix", "Paris", "75001",
                LogementType.STUDIO, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/logements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/v1/logements/{id} ──────────────────────────────────────────

    @Test
    @WithMockUser
    void shouldReturn200WhenLogementExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(logementService.getLogement(id)).thenReturn(fakeResponse());

        mockMvc.perform(get("/api/v1/logements/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adresse").value("12 rue de la Paix"));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenLogementNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(logementService.getLogement(id))
                .thenThrow(new ResourceNotFoundException("Logement introuvable"));

        mockMvc.perform(get("/api/v1/logements/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/v1/logements/{id}/publish ──────────────────────────────────

    @Test
    @WithMockUser(username = "pierre@studup.fr")
    void shouldReturn200WhenPublishSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        LogementResponse published = new LogementResponse(
                id, UUID.randomUUID(), "12 rue de la Paix", "Paris", "75001",
                null, null, LogementType.STUDIO, new BigDecimal("25.00"), 1,
                new BigDecimal("800.00"), new BigDecimal("50.00"), null, null,
                LogementStatut.ACTIF, false, true, null, List.of(), OffsetDateTime.now(), "Pierre"
        );

        // @PreAuthorize passe → securityService retourne true
        when(securityService.isLogementOwner(eq(id), any())).thenReturn(true);
        when(logementService.publishLogement(eq("pierre@studup.fr"), eq(id))).thenReturn(published);

        mockMvc.perform(put("/api/v1/logements/{id}/publish", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"));
    }

    @Test
    @WithMockUser(username = "autre@studup.fr")
    void shouldReturn403WhenPublishByNonOwner() throws Exception {
        UUID id = UUID.randomUUID();
        // @PreAuthorize passe (mock retourne true), mais le service détecte que ce n'est pas l'owner
        when(securityService.isLogementOwner(eq(id), any())).thenReturn(true);
        when(logementService.publishLogement(eq("autre@studup.fr"), eq(id)))
                .thenThrow(new UnauthorizedException("Vous n'êtes pas le propriétaire de ce logement"));

        mockMvc.perform(put("/api/v1/logements/{id}/publish", id).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ─── PATCH /api/v1/logements/{id}/ville ──────────────────────────────────

    @Test
    @WithMockUser(username = "pierre@studup.fr")
    void shouldReturn200WhenAssocierVilleSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        LogementResponse associated = new LogementResponse(
                id, UUID.randomUUID(), "12 rue de la Paix", "Paris", "75001",
                null, null, LogementType.STUDIO, new BigDecimal("25.00"), 1,
                new BigDecimal("800.00"), new BigDecimal("50.00"), null, null,
                LogementStatut.ACTIF, false, true, VilleAssociee.VILLE_A, List.of(), OffsetDateTime.now(), "Pierre"
        );

        // @PreAuthorize passe → securityService retourne true
        when(securityService.isLogementOwner(eq(id), any())).thenReturn(true);
        when(logementService.associerVille(eq("pierre@studup.fr"), eq(id), any()))
                .thenReturn(associated);

        mockMvc.perform(patch("/api/v1/logements/{id}/ville", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"villeAssociee\":\"VILLE_A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.villeAssociee").value("VILLE_A"));
    }

    @Test
    @WithMockUser(username = "pierre@studup.fr")
    void shouldReturn400WhenVilleAssocieeIsMissing() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/logements/{id}/ville", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"villeAssociee\":null}"))
                .andExpect(status().isBadRequest());
    }
}
