package com.studup.backend.controller;

import com.studup.backend.model.dto.response.CandidatureResponse;
import com.studup.backend.model.enums.CandidatureStatut;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.security.SecurityConfig;
import com.studup.backend.service.CandidatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CandidatureController.class)
@Import(SecurityConfig.class)
class CandidatureControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CandidatureService candidatureService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;

    /** Le logement est à null : ce test valide le contrat HTTP, pas le mapping. */
    private CandidatureResponse sample() {
        return new CandidatureResponse(
                UUID.randomUUID(), CandidatureStatut.CONTACTE, "Visite jeudi",
                OffsetDateTime.now(), OffsetDateTime.now(), null);
    }

    @Test
    @WithMockUser(username = "massi@studup.fr")
    void shouldReturn200WithMesCandidatures() throws Exception {
        when(candidatureService.getMesCandidatures("massi@studup.fr"))
                .thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/v1/candidatures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statut").value("CONTACTE"))
                .andExpect(jsonPath("$[0].note").value("Visite jeudi"));
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/candidatures"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "massi@studup.fr")
    void shouldReturn201WhenFollowingALogement() throws Exception {
        when(candidatureService.suivre(any(), any(), any())).thenReturn(sample());

        mockMvc.perform(post("/api/v1/candidatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logementId\":\"" + UUID.randomUUID()
                                + "\",\"statut\":\"CONTACTE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("CONTACTE"));
    }

    @Test
    @WithMockUser(username = "massi@studup.fr")
    void shouldReturn400WhenLogementIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/candidatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
