package com.studup.backend.controller;

import com.studup.backend.model.dto.response.UserResponse;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.security.CustomUserDetailsService;
import com.studup.backend.security.JwtBlacklistService;
import com.studup.backend.security.JwtUtil;
import com.studup.backend.security.SecurityConfig;
import com.studup.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private UserService userService;

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WithCurrentUser() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@studup.fr")
                .passwordHash("hash")
                .firstName("Alice")
                .lastName("Martin")
                .role(UserRole.ALTERNANT)
                .isVerified(true)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(userRepository.findByEmail("alice@studup.fr"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.role").value("ALTERNANT"))
                // Le hash du mot de passe ne doit JAMAIS sortir
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // APP-117 : changement de mode étudiant ⇄ alternant
    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn200WhenChangingMode() throws Exception {
        when(userService.changeMode(any(), any())).thenReturn(new UserResponse(
                UUID.randomUUID(), "alice@studup.fr", "Alice", "Martin",
                UserRole.ALTERNANT, true, OffsetDateTime.now()));

        mockMvc.perform(patch("/api/v1/users/me/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ALTERNANT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ALTERNANT"));
    }

    @Test
    @WithMockUser(username = "alice@studup.fr")
    void shouldReturn400WhenRoleMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
