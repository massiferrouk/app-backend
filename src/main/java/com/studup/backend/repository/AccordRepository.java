package com.studup.backend.repository;

import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.enums.AccordStatut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccordRepository extends JpaRepository<Accord, UUID> {

    // Historique des accords d'un utilisateur (initiateur ou destinataire), paginé
    @Query("""
            SELECT a FROM Accord a
            WHERE a.initiatorId = :userId OR a.receiverId = :userId
            ORDER BY a.createdAt DESC
            """)
    Page<Accord> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Existe-t-il un accord lié à ce logement (comme logement A ou B) ?
    // Sert à empêcher la suppression d'un logement engagé dans un accord.
    boolean existsByLogementAIdOrLogementBId(UUID logementAId, UUID logementBId);

    /**
     * Expire tous les accords EN_ATTENTE créés avant la limite de temps donnée.
     * Retourne le nombre d'accords modifiés.
     */
    @Modifying
    @Query("""
            UPDATE Accord a
            SET a.statut = :nouveauStatut, a.updatedAt = CURRENT_TIMESTAMP
            WHERE a.statut = 'EN_ATTENTE'
            AND a.createdAt < :limite
            """)
    int expireAccordsEnAttente(
            @Param("limite") OffsetDateTime limite,
            @Param("nouveauStatut") AccordStatut nouveauStatut
    );

    // Prochains accords actifs/acceptés sur les 8 semaines à venir
    @Query("""
            SELECT a FROM Accord a
            WHERE (a.initiatorId = :userId OR a.receiverId = :userId)
            AND a.statut IN ('EN_COURS', 'ACCEPTE')
            AND (a.dateDebut IS NULL OR a.dateDebut <= :maxDate)
            ORDER BY a.dateDebut ASC NULLS LAST
            """)
    List<Accord> findProchainAccords(@Param("userId") UUID userId,
                                     @Param("maxDate") LocalDate maxDate);

    // Accords EN_ATTENTE où l'utilisateur est destinataire — il doit répondre
    @Query("""
            SELECT a FROM Accord a
            WHERE a.receiverId = :userId
            AND a.statut = 'EN_ATTENTE'
            ORDER BY a.createdAt ASC
            """)
    List<Accord> findAccordsEnAttenteForReceiver(@Param("userId") UUID userId);

    // Accords terminés de type échange pour le calcul des économies
    @Query("""
            SELECT a FROM Accord a
            WHERE (a.initiatorId = :userId OR a.receiverId = :userId)
            AND a.statut = 'TERMINE'
            AND a.type IN ('ECHANGE_TOTAL', 'ECHANGE_PARTIEL', 'COLOCATION_TOURNANTE')
            """)
    List<Accord> findAccordsTerminesEchange(@Param("userId") UUID userId);

    // Retourne les accords actifs (EN_COURS ou ACCEPTE) avec des dates, pour l'export iCal
    @Query("""
            SELECT a FROM Accord a
            WHERE (a.initiatorId = :userId OR a.receiverId = :userId)
            AND a.statut IN ('EN_COURS', 'ACCEPTE')
            AND a.dateDebut IS NOT NULL
            AND a.dateFin IS NOT NULL
            """)
    List<Accord> findActiveAccordsForUser(@Param("userId") UUID userId);

    // Retourne les IDs des logements actuellement occupés (dans un accord EN_COURS)
    // Cherche dans les deux colonnes logement_a_id et logement_b_id
    @Query(value = """
            SELECT logement_a_id AS id FROM accords
              WHERE logement_a_id IN :ids AND statut = 'EN_COURS'
            UNION
            SELECT logement_b_id AS id FROM accords
              WHERE logement_b_id IN :ids AND statut = 'EN_COURS' AND logement_b_id IS NOT NULL
            """, nativeQuery = true)
    List<UUID> findOccupiedLogementIds(@Param("ids") List<UUID> ids);
}
