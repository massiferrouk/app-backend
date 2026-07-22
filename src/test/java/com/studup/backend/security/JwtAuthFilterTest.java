package com.studup.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Le filtre est le seul endroit où la révocation est effectivement appliquée :
 * un token peut être parfaitement valide cryptographiquement et pourtant ne
 * plus devoir donner accès à quoi que ce soit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private JwtBlacklistService jwtBlacklistService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private Claims claims;

    @InjectMocks
    private JwtAuthFilter filter;

    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Token valide, non révoqué : l'utilisateur est authentifié. */
    private void tokenValide() {
        UserDetails details = User.builder()
                .username("bob@studup.fr").password("x")
                .authorities(List.of()).build();

        when(request.getHeader("Authorization")).thenReturn("Bearer jwt");
        when(jwtUtil.isTokenValid("jwt")).thenReturn(true);
        when(jwtUtil.extractJti("jwt")).thenReturn("jti-1");
        when(jwtUtil.extractUserId("jwt")).thenReturn(userId);
        when(jwtUtil.extractClaims("jwt")).thenReturn(claims);
        when(claims.get("email", String.class)).thenReturn("bob@studup.fr");
        when(userDetailsService.loadUserByUsername("bob@studup.fr")).thenReturn(details);
    }

    @Test
    void shouldAuthenticateWhenTokenIsValid() throws Exception {
        tokenValide();
        when(jwtBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        when(jwtBlacklistService.isUserRevoked(userId)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectTokenOfSuspendedUser() throws Exception {
        // Compte suspendu par un admin : le token reste valide et absent de la
        // blacklist individuelle (aucun logout), mais tous les tokens de cet
        // utilisateur ont été révoqués en bloc. Sans ce contrôle, un compte
        // banni continuait d'être servi jusqu'à l'expiration de son token.
        tokenValide();
        when(jwtBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        when(jwtBlacklistService.isUserRevoked(userId)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // La requête continue : c'est Spring Security qui répondra 401/403
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectBlacklistedToken() throws Exception {
        tokenValide();
        when(jwtBlacklistService.isBlacklisted("jti-1")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void shouldSkipWhenNoBearerHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtUtil, never()).isTokenValid(anyString());
        verify(filterChain).doFilter(request, response);
    }
}
