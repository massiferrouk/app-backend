package com.yuniv.backend.service;

import com.yuniv.backend.exception.DuplicateEmailException;
import com.yuniv.backend.exception.UnauthorizedException;
import com.yuniv.backend.model.dto.request.LoginRequest;
import com.yuniv.backend.model.dto.request.RefreshRequest;
import com.yuniv.backend.model.dto.request.RegisterRequest;
import com.yuniv.backend.model.dto.response.AuthResponse;
import com.yuniv.backend.model.dto.response.UserResponse;
import com.yuniv.backend.model.entity.RefreshToken;
import com.yuniv.backend.model.entity.User;
import com.yuniv.backend.model.enums.UserRole;
import com.yuniv.backend.repository.RefreshTokenRepository;
import com.yuniv.backend.repository.UserRepository;
import com.yuniv.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailConfirmationService emailConfirmationService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    // ─── Tests inscription ────────────────────────────────────────────────────

    @Test
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr",
                "motdepasse123",
                "Alice",
                "Martin",
                UserRole.ALTERNANT
        );

        when(userRepository.existsByEmail("alice@yuniv.fr")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse123")).thenReturn("$2a$10$hashedpassword");

        User savedUser = buildUser("alice@yuniv.fr");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("alice@yuniv.fr");
        assertThat(response.firstName()).isEqualTo("Alice");
        assertThat(response.role()).isEqualTo(UserRole.ALTERNANT);
        assertThat(response.isVerified()).isFalse();

        verify(passwordEncoder).encode("motdepasse123");
        verify(emailConfirmationService).sendConfirmationEmail(any(User.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr",
                "motdepasse123",
                "Alice",
                "Martin",
                UserRole.ALTERNANT
        );

        when(userRepository.existsByEmail("alice@yuniv.fr")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Un compte existe déjà avec cet email");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(emailConfirmationService, never()).sendConfirmationEmail(any());
    }

    // ─── Tests connexion ──────────────────────────────────────────────────────

    @Test
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("alice@yuniv.fr", "motdepasse123");

        User user = buildUser("alice@yuniv.fr");

        // authenticationManager ne lève pas d'exception → credentials valides
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail("alice@yuniv.fr")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any(), anyString(), anyString())).thenReturn("fake-access-token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("fake-refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(null);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("fake-access-token");
        assertThat(response.refreshToken()).isEqualTo("fake-refresh-token");

        // Vérifie que le refresh token a bien été sauvegardé en BDD
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void shouldRejectBadCredentials() {
        LoginRequest request = new LoginRequest("alice@yuniv.fr", "mauvais-mdp");

        // authenticationManager lève une exception → credentials invalides
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        // Aucun token ne doit être généré ni sauvegardé
        verify(jwtUtil, never()).generateAccessToken(any(), anyString(), anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    // ─── Tests renouvellement ─────────────────────────────────────────────────

    @Test
    void shouldRefreshTokens() {
        RefreshRequest request = new RefreshRequest("valid-refresh-token");
        UUID userId = UUID.randomUUID();
        User user = buildUser("alice@yuniv.fr");

        when(jwtUtil.isTokenValid("valid-refresh-token")).thenReturn(true);

        // Simule un token en base, non révoqué, non expiré
        RefreshToken storedToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash("some-hash")
                .isRevoked(false)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any(), anyString(), anyString())).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("new-refresh-token");

        AuthResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");

        // Vérifie que l'ancien token a bien été révoqué (rotation)
        assertThat(storedToken.getIsRevoked()).isTrue();
    }

    @Test
    void shouldRejectRevokedRefreshToken() {
        RefreshRequest request = new RefreshRequest("revoked-token");

        when(jwtUtil.isTokenValid("revoked-token")).thenReturn(true);

        RefreshToken revokedToken = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("some-hash")
                .isRevoked(true)   // déjà révoqué
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token has been revoked");
    }

    // ─── Tests déconnexion ────────────────────────────────────────────────────

    @Test
    void shouldLogout() {
        RefreshRequest request = new RefreshRequest("valid-refresh-token");

        RefreshToken storedToken = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("some-hash")
                .isRevoked(false)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(null);

        authService.logout(request);

        // Vérifie que le token a bien été révoqué
        assertThat(storedToken.getIsRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private User buildUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("$2a$10$hashedpassword")
                .firstName("Alice")
                .lastName("Martin")
                .role(UserRole.ALTERNANT)
                .isVerified(false)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
