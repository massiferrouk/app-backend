package com.studup.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private JwtBlacklistService jwtBlacklistService;

    @Test
    void shouldBlacklistTokenWithTtl() {
        String jti = "abc-123";
        Duration ttl = Duration.ofMinutes(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        jwtBlacklistService.blacklist(jti, ttl);

        // Vérifie que Redis a bien reçu la clé avec le TTL exact
        verify(valueOperations).set("jwt:blacklist:abc-123", "revoked", ttl);
    }

    @Test
    void shouldReturnTrueWhenTokenIsBlacklisted() {
        when(redisTemplate.hasKey("jwt:blacklist:abc-123")).thenReturn(true);

        boolean result = jwtBlacklistService.isBlacklisted("abc-123");

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTokenIsNotBlacklisted() {
        when(redisTemplate.hasKey("jwt:blacklist:unknown")).thenReturn(false);

        boolean result = jwtBlacklistService.isBlacklisted("unknown");

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenRedisReturnsNull() {
        // Redis peut retourner null si la clé n'existe pas — on doit gérer ce cas
        when(redisTemplate.hasKey("jwt:blacklist:abc-123")).thenReturn(null);

        boolean result = jwtBlacklistService.isBlacklisted("abc-123");

        assertThat(result).isFalse();
    }

    @Test
    void shouldRestoreUserByDeletingRevocationKey() {
        UUID userId = UUID.randomUUID();

        jwtBlacklistService.restoreUser(userId);

        // La cle de revocation vit 24 h : sans cette suppression, reactiver un
        // compte ne suffirait pas, JwtAuthFilter continuerait de le refuser.
        verify(redisTemplate).delete("jwt:revoked:user:" + userId);
    }
}
