package com.studup.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.OverrideScheduleRequest;
import com.studup.backend.model.dto.response.AlternanceScheduleResponse;
import com.studup.backend.model.enums.CompatibiliteType;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.CalendrierService;
import com.studup.backend.service.DisponibiliteService;
import com.studup.backend.service.MatchingService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CalendrierController.class)
class CalendrierControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CalendrierService calendrierService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private DisponibiliteService disponibiliteService;
    @MockitoBean private MatchingService matchingService;

    // ─── GET /api/v1/calendrier/compatibilite ────────────────────────────────

    @Test
    @WithMockUser
    void shouldReturn200WithColorCodedWeeks() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        List<SemaineCompatibilite> semaines = List.of(
                SemaineCompatibilite.of(LocalDate.of(2026, 1, 5), "Lyon", "Paris", CompatibiliteType.ECHANGE),
                SemaineCompatibilite.of(LocalDate.of(2026, 1, 12), "Paris", "Paris", CompatibiliteType.CHEVAUCHEMENT)
        );

        when(calendrierService.getCalendrierCompatibilite(any(), any())).thenReturn(semaines);

        mockMvc.perform(get("/api/v1/calendrier/compatibilite")
                        .param("user1", user1.toString())
                        .param("user2", user2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("ECHANGE"))
                .andExpect(jsonPath("$[0].couleurHex").value("#27AE60"))
                .andExpect(jsonPath("$[0].label").value("Échange"))
                .andExpect(jsonPath("$[1].type").value("CHEVAUCHEMENT"))
                .andExpect(jsonPath("$[1].couleurHex").value("#F39C12"));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenProfileNotFound() throws Exception {
        when(calendrierService.getCalendrierCompatibilite(any(), any()))
                .thenThrow(new ResourceNotFoundException("Profil alternant introuvable"));

        mockMvc.perform(get("/api/v1/calendrier/compatibilite")
                        .param("user1", UUID.randomUUID().toString())
                        .param("user2", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void shouldReturn200WithEmptyListWhenNoWeeks() throws Exception {
        when(calendrierService.getCalendrierCompatibilite(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/calendrier/compatibilite")
                        .param("user1", UUID.randomUUID().toString())
                        .param("user2", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenUserParamMissing() throws Exception {
        mockMvc.perform(get("/api/v1/calendrier/compatibilite")
                        .param("user1", UUID.randomUUID().toString()))
                // user2 manquant → Spring renvoie 400 automatiquement
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/calendrier/compatibilite")
                        .param("user1", UUID.randomUUID().toString())
                        .param("user2", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    // ─── PATCH /api/v1/calendrier/{profileId}/semaines/{semaine} ─────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WhenOverrideSucceeds() throws Exception {
        UUID profileId = UUID.randomUUID();
        LocalDate semaine = LocalDate.now().plusWeeks(1)
                .with(java.time.DayOfWeek.MONDAY);

        AlternanceScheduleResponse response = new AlternanceScheduleResponse(
                UUID.randomUUID(), semaine, "B", true, "conges");

        when(calendrierService.overrideSemaine(
                eq("alice@studup.fr"), eq(profileId), eq(semaine), any()))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/calendrier/" + profileId + "/semaines/" + semaine)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OverrideScheduleRequest("B", "conges"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("B"))
                .andExpect(jsonPath("$.isOverridden").value(true))
                .andExpect(jsonPath("$.overrideReason").value("conges"));
    }

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn400WhenLabelInvalid() throws Exception {
        UUID profileId = UUID.randomUUID();
        LocalDate semaine = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);

        mockMvc.perform(patch("/api/v1/calendrier/" + profileId + "/semaines/" + semaine)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OverrideScheduleRequest("C", "conges"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "bob@studup.fr")
    void shouldReturn403WhenNotOwner() throws Exception {
        UUID profileId = UUID.randomUUID();
        LocalDate semaine = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);

        when(calendrierService.overrideSemaine(any(), any(), any(), any()))
                .thenThrow(new UnauthorizedException("Vous ne pouvez modifier que votre propre calendrier"));

        mockMvc.perform(patch("/api/v1/calendrier/" + profileId + "/semaines/" + semaine)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OverrideScheduleRequest("B", "conges"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn401OnOverrideWhenNotAuthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/calendrier/" + UUID.randomUUID()
                        + "/semaines/" + LocalDate.now().plusWeeks(1))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OverrideScheduleRequest("B", "conges"))))
                .andExpect(status().isUnauthorized());
    }
}
