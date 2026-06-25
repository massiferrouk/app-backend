package com.studup.backend.controller;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.ReputationScoreResponse;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.ReputationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReputationController.class)
class ReputationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ReputationService reputationService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    // ─── GET /reputation/user/{userId} ───────────────────────────────────────

    @Test
    @WithMockUser
    void shouldReturn200WithScore() throws Exception {
        UUID userId = UUID.randomUUID();
        ReputationScoreResponse response = new ReputationScoreResponse(
                userId, new BigDecimal("4.20"), 12,
                new BigDecimal("4.50"), 8, "Expert");

        when(reputationService.getScore(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/reputation/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgRating").value(4.20))
                .andExpect(jsonPath("$.totalReviews").value(12))
                .andExpect(jsonPath("$.badge").value("Expert"));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenUserHasNoScore() throws Exception {
        UUID userId = UUID.randomUUID();
        when(reputationService.getScore(userId))
                .thenThrow(new ResourceNotFoundException("Score introuvable"));

        mockMvc.perform(get("/api/v1/reputation/user/" + userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/reputation/user/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
