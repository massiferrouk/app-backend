package com.studup.backend.controller;

import com.studup.backend.model.dto.response.AlternantDashboardResponse;
import com.studup.backend.model.dto.response.ProprietaireDashboardResponse;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.security.SecurityConfig;
import com.studup.backend.service.AlternantDashboardService;
import com.studup.backend.service.ProprietaireDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProprietaireDashboardService proprietaireDashboardService;
    @MockitoBean private AlternantDashboardService alternantDashboardService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;

    // ─── GET /api/v1/dashboard/proprietaire ──────────────────────────────────

    @Test
    @WithMockUser(username = "proprio@studup.fr")
    void shouldReturn200WithDashboard() throws Exception {
        ProprietaireDashboardResponse response = new ProprietaireDashboardResponse(
                2, 2, 3, 2, List.of());

        when(proprietaireDashboardService.getDashboard(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/dashboard/proprietaire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbLogementsTotaux").value(2))
                .andExpect(jsonPath("$.nbEtudiantsInteresses").value(3))
                .andExpect(jsonPath("$.nbConversations").value(2));
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/proprietaire"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/v1/dashboard/alternant ─────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithAlternantDashboard() throws Exception {
        AlternantDashboardResponse response = new AlternantDashboardResponse(
                List.of(), List.of(), BigDecimal.valueOf(2700), 1);

        when(alternantDashboardService.getDashboard(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/dashboard/alternant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbMatchesCompatibles").value(1))
                .andExpect(jsonPath("$.economiePossibleMax").value(2700));
    }

    @Test
    void shouldReturn401OnAlternantDashboardWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/alternant"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "proprio@studup.fr")
    void shouldReturn200WithEmptyDashboard() throws Exception {
        ProprietaireDashboardResponse empty = new ProprietaireDashboardResponse(
                0, 0, 0, 0, List.of());

        when(proprietaireDashboardService.getDashboard(any())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/dashboard/proprietaire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbLogementsTotaux").value(0))
                .andExpect(jsonPath("$.nbEtudiantsInteresses").value(0))
                .andExpect(jsonPath("$.logements").isArray());
    }
}
