package com.yuniv.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuniv.backend.exception.DuplicateEmailException;
import com.yuniv.backend.model.dto.request.RegisterRequest;
import com.yuniv.backend.model.dto.response.UserResponse;
import com.yuniv.backend.model.enums.UserRole;
import com.yuniv.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
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

    // Cas nominal : inscription réussie → 201
    @Test
    void shouldReturn201OnValidRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr",
                "motdepasse123",
                "Alice",
                "Martin",
                UserRole.ALTERNANT
        );

        UserResponse fakeResponse = new UserResponse(
                UUID.randomUUID(),
                "alice@yuniv.fr",
                "Alice",
                "Martin",
                UserRole.ALTERNANT,
                false,
                OffsetDateTime.now()
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

    // Cas d'erreur : email invalide → 400
    @Test
    void shouldReturn400WhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "pas-un-email",
                "motdepasse123",
                "Alice",
                "Martin",
                UserRole.ALTERNANT
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    // Cas d'erreur : mot de passe trop court → 400
    @Test
    void shouldReturn400WhenPasswordIsTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr",
                "123",
                "Alice",
                "Martin",
                UserRole.ALTERNANT
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // Cas d'erreur : email déjà utilisé → 409
    @Test
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr",
                "motdepasse123",
                "Alice",
                "Martin",
                UserRole.ALTERNANT
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
}
