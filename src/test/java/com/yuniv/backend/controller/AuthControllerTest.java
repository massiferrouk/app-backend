package com.yuniv.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuniv.backend.exception.DuplicateEmailException;
import com.yuniv.backend.model.dto.request.LoginRequest;
import com.yuniv.backend.model.dto.request.RefreshRequest;
import com.yuniv.backend.model.dto.request.RegisterRequest;
import com.yuniv.backend.model.dto.response.AuthResponse;
import com.yuniv.backend.model.dto.response.UserResponse;
import com.yuniv.backend.model.enums.UserRole;
import com.yuniv.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // ─── Tests inscription ────────────────────────────────────────────────────

    @Test
    void shouldReturn201OnValidRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr", "motdepasse123", "Alice", "Martin", UserRole.ALTERNANT
        );

        UserResponse fakeResponse = new UserResponse(
                UUID.randomUUID(), "alice@yuniv.fr", "Alice", "Martin",
                UserRole.ALTERNANT, false, OffsetDateTime.now()
        );

        when(authService.register(any(RegisterRequest.class))).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@yuniv.fr"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.isVerified").value(false));
    }

    @Test
    void shouldReturn400WhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "pas-un-email", "motdepasse123", "Alice", "Martin", UserRole.ALTERNANT
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void shouldReturn400WhenPasswordIsTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr", "123", "Alice", "Martin", UserRole.ALTERNANT
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr", "motdepasse123", "Alice", "Martin", UserRole.ALTERNANT
        );

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException("Un compte existe déjà avec cet email"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("Un compte existe déjà avec cet email"));
    }

    // ─── Tests connexion ──────────────────────────────────────────────────────

    @Test
    void shouldReturn200OnValidLogin() throws Exception {
        LoginRequest request = new LoginRequest("alice@yuniv.fr", "motdepasse123");
        AuthResponse fakeResponse = new AuthResponse("fake-access-token", "fake-refresh-token");

        when(authService.login(any(LoginRequest.class))).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("fake-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("fake-refresh-token"));
    }

    @Test
    void shouldReturn401OnBadCredentials() throws Exception {
        LoginRequest request = new LoginRequest("alice@yuniv.fr", "mauvais-mdp");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─── Tests renouvellement ─────────────────────────────────────────────────

    @Test
    void shouldReturn200OnValidRefresh() throws Exception {
        RefreshRequest request = new RefreshRequest("valid-refresh-token");
        AuthResponse fakeResponse = new AuthResponse("new-access-token", "new-refresh-token");

        when(authService.refresh(any(RefreshRequest.class))).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    // ─── Tests déconnexion ────────────────────────────────────────────────────

    @Test
    void shouldReturn204OnLogout() throws Exception {
        RefreshRequest request = new RefreshRequest("valid-refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }
}
