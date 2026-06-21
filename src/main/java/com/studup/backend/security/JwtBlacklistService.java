package com.studup.backend.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Gère la blacklist des JWT révoqués dans Redis.
 * Clé Redis : "jwt:blacklist:{jti}"
 * TTL = temps restant avant expiration du token — Redis supprime l'entrée automatiquement.
 */
@Service
public class JwtBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public JwtBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Ajoute un JTI dans la blacklist avec un TTL égal au temps restant avant expiration.
     * Appelé au logout.
     */
    public void blacklist(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "revoked", ttl);
    }

    /**
     * Vérifie si un JTI est dans la blacklist.
     * Appelé par JwtAuthFilter à chaque requête authentifiée.
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    /**
     * Révoque tous les tokens d'un utilisateur (suspension, bannissement).
     * Clé Redis : "jwt:revoked:user:{userId}" — durée 24h (couvre les tokens 15min actifs).
     * JwtAuthFilter doit vérifier cette clé en plus de la blacklist individuelle.
     */
    public void revokeAllForUser(UUID userId) {
        redisTemplate.opsForValue().set(
                "jwt:revoked:user:" + userId,
                "revoked",
                Duration.ofHours(24)
        );
    }

    /**
     * Vérifie si tous les tokens d'un utilisateur sont révoqués.
     */
    public boolean isUserRevoked(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("jwt:revoked:user:" + userId));
    }
}
