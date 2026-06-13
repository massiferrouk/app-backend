package com.studup.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Utilitaire JWT : génère et valide les access tokens et refresh tokens.
 * Ne connaît pas Spring Security, ne touche pas à la base de données.
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        // Keys.hmacShaKeyFor génère une clé HMAC-SHA à partir de notre secret
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Génère un access token JWT (durée : 15 min par défaut).
     * Claims inclus : userId, email, role, jti (identifiant unique du token).
     */
    public String generateAccessToken(UUID userId, String email, String role) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())        // jti — sert à blacklister le token
                .subject(userId.toString())              // "sub" — identifiant principal
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Génère un refresh token JWT (durée : 7 jours par défaut).
     * Ne contient que le userId — le refresh token ne doit pas exposer de données sensibles.
     */
    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extrait tous les claims d'un token.
     * Lance une JwtException si le token est invalide ou expiré.
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Retourne true si le token est valide (signature correcte + non expiré).
     */
    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Extrait le userId (claim "sub") depuis le token. */
    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).getSubject());
    }

    /** Extrait le JTI (identifiant unique du token) — utilisé pour la blacklist. */
    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    /** Extrait le rôle depuis le token — uniquement présent dans les access tokens. */
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    /** Retourne la date d'expiration du token. */
    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }
}
