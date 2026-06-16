package com.studup.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studup.backend.model.dto.request.LoginRequest;
import com.studup.backend.model.dto.request.RegisterRequest;
import com.studup.backend.model.dto.response.AuthResponse;
import com.studup.backend.model.dto.response.UserResponse;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.service.AlternantProfileService;
import com.studup.backend.service.AuthService;
import com.studup.backend.service.LogementService;
import com.studup.backend.service.ProprietaireProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @DirtiesContext force Spring à détruire le contexte après ce test.
// Sans ça, le RateLimitingFilter (qui garde ses buckets en mémoire) polluerait
// les tests suivants : les tokens consommés ici rendraient d'autres tests instables.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@WebMvcTest(excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class RateLimitingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AlternantProfileService alternantProfileService;

    @MockitoBean
    private ProprietaireProfileService proprietaireProfileService;

    @MockitoBean
    private LogementService logementService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // ─── Tests /auth/login ────────────────────────────────────────────────────

    @Test
    void shouldReturn429AfterFiveLoginAttempts() throws Exception {
        LoginRequest request = new LoginRequest("alice@yuniv.fr", "motdepasse123");
        String body = objectMapper.writeValueAsString(request);

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("access-token", "refresh-token"));

        // Les 5 premières requêtes passent le filtre → 200 OK
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        // La 6ème est bloquée par le filtre → 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    // ─── Tests /auth/register ─────────────────────────────────────────────────

    @Test
    void shouldReturn429AfterThreeRegisterAttempts() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr", "motdepasse123", "Alice", "Martin", UserRole.ALTERNANT
        );
        String body = objectMapper.writeValueAsString(request);

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(
                        UUID.randomUUID(), "alice@yuniv.fr", "Alice", "Martin",
                        UserRole.ALTERNANT, false, OffsetDateTime.now()
                ));

        // Les 3 premières requêtes passent le filtre → 201 Created
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        // La 4ème est bloquée par le filtre → 429
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
