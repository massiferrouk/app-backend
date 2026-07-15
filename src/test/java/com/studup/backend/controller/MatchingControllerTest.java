package com.studup.backend.controller;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MatchingSuggestionResponse;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.DisponibiliteService;
import com.studup.backend.service.MatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MatchingController.class)
class MatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private MatchingService matchingService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private DisponibiliteService disponibiliteService;

    private MatchingSuggestionResponse fakeSuggestion(double score, AccordType type) {
        return new MatchingSuggestionResponse(
                UUID.randomUUID(), UUID.randomUUID(),
                "Bob", "Dupont",
                "Lyon", "Paris",
                score, (int) Math.round(score * 100),
                type, false,
                "Publiez vos logements pour activer ce match",
                3, 0, 1, "résumé", List.of(),
                UUID.randomUUID(), UUID.randomUUID(),
                new java.math.BigDecimal("225")
        );
    }

    // ─── GET /api/v1/matching/suggestions ────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithSuggestions() throws Exception {
        List<MatchingSuggestionResponse> suggestions = List.of(
                fakeSuggestion(1.0, AccordType.ECHANGE_TOTAL),
                fakeSuggestion(0.75, AccordType.ECHANGE_PARTIEL)
        );

        when(matchingService.getSuggestions("alice@studup.fr")).thenReturn(suggestions);

        mockMvc.perform(get("/api/v1/matching/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].score").value(1.0))
                .andExpect(jsonPath("$[0].typePropose").value("ECHANGE_TOTAL"))
                .andExpect(jsonPath("$[0].scorePercent").value(100))
                .andExpect(jsonPath("$[1].typePropose").value("ECHANGE_PARTIEL"));
    }

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithEmptyListWhenNoSuggestions() throws Exception {
        when(matchingService.getSuggestions("alice@studup.fr")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/matching/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn404WhenProfileNotFound() throws Exception {
        when(matchingService.getSuggestions(anyString()))
                .thenThrow(new ResourceNotFoundException("Profil alternant introuvable"));

        mockMvc.perform(get("/api/v1/matching/suggestions"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/matching/suggestions"))
                .andExpect(status().isUnauthorized());
    }
}
