package com.yuniv.backend.repository;

import com.yuniv.backend.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Cherche un token par son hash — utilisé lors du refresh et du logout
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Révoque tous les refresh tokens d'un utilisateur — utilisé lors du changement de mot de passe
    // ou de la suspension du compte par un admin
    @Modifying
    @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.userId = :userId")
    void revokeAllByUserId(UUID userId);
}
