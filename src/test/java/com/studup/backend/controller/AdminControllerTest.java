package com.studup.backend.controller;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.AdminService;
import com.studup.backend.service.CalendrierService;
import com.studup.backend.service.DisponibiliteService;
import com.studup.backend.service.MatchingService;
import com.studup.backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AdminService adminService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private DisponibiliteService disponibiliteService;
    @MockitoBean private MatchingService matchingService;
    @MockitoBean private CalendrierService calendrierService;

    private AdminUserResponse fakeUser(UUID id, boolean isActive) {
        return new AdminUserResponse(id, "bob@studup.fr", "Bob", "Dupont",
                UserRole.ALTERNANT, true, isActive, OffsetDateTime.now(), null);
    }

    // ─── GET /api/v1/admin/users ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn200WithUserList() throws Exception {
        UUID userId = UUID.randomUUID();
        Page<AdminUserResponse> page = new PageImpl<>(
                List.of(fakeUser(userId, true)),
                PageRequest.of(0, 20), 1);

        when(adminService.listUsers(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("bob@studup.fr"));
    }

    @Test
    void shouldReturn403WhenNotAuthenticated() throws Exception {
        // Sans JWT, Spring Security renvoie 403 (pas d'AuthenticationEntryPoint JWT configuré en @WebMvcTest)
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ALTERNANT")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    // ─── PUT /api/v1/admin/users/{id}/suspend ────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@studup.fr")
    void shouldReturn200OnSuspend() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.suspendUser(eq(userId), any())).thenReturn(fakeUser(userId, false));

        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@studup.fr")
    void shouldReturn404WhenUserNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(adminService.suspendUser(eq(unknownId), any()))
                .thenThrow(new ResourceNotFoundException("Utilisateur introuvable"));

        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", unknownId))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/v1/admin/users/{id}/ban ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@studup.fr")
    void shouldReturn200OnBan() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.banUser(eq(userId), any())).thenReturn(fakeUser(userId, false));

        mockMvc.perform(put("/api/v1/admin/users/{id}/ban", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }
}
