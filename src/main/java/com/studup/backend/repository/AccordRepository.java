package com.studup.backend.repository;

import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.enums.AccordStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AccordRepository extends JpaRepository<Accord, UUID> {

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
}
