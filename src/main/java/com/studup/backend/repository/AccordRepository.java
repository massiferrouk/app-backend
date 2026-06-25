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
