package com.studup.backend.repository;

import com.studup.backend.model.entity.AlternantProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlternantProfileRepository extends JpaRepository<AlternantProfile, UUID> {

    boolean existsByUserId(UUID userId);

    Optional<AlternantProfile> findByUserId(UUID userId);

    // Présélection : profils qui ont au moins une ville en commun avec les villes données.
    // Exclut le profil de l'utilisateur connecté lui-même.
    // APP-92 : comparaison insensible à la casse (LOWER des deux côtés) — cohérent
    // avec le CompatibilityCalculator qui utilise equalsIgnoreCase.
    @Query("""
            SELECT p FROM AlternantProfile p
            WHERE p.id != :excludeProfileId
            AND (
                LOWER(p.villeA) IN (LOWER(:villeA), LOWER(:villeB))
                OR LOWER(p.villeB) IN (LOWER(:villeA), LOWER(:villeB))
            )
            """)
    List<AlternantProfile> findCandidatesWithSharedCity(
            @Param("excludeProfileId") UUID excludeProfileId,
            @Param("villeA") String villeA,
            @Param("villeB") String villeB
    );
}
