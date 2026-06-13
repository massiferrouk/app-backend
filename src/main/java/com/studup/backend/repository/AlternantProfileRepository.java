package com.studup.backend.repository;

import com.studup.backend.model.entity.AlternantProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlternantProfileRepository extends JpaRepository<AlternantProfile, UUID> {

    // Vérifie si un profil existe déjà pour cet utilisateur
    boolean existsByUserId(UUID userId);

    // Récupère le profil d'un utilisateur donné
    Optional<AlternantProfile> findByUserId(UUID userId);
}
