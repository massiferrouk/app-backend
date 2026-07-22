package com.studup.backend.repository;

import com.studup.backend.model.entity.Accord;
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
     * Existe-t-il un accord VIVANT lié à ce logement ? (APP-117 · A-06)
     *
     * Un accord vivant = une négociation ou un contrat en cours :
     * EN_ATTENTE, ACCEPTE, EN_COURS, LITIGE. Les accords morts
     * (REFUSE, ANNULE, TERMINE) ne verrouillent plus le logement :
     * l'utilisateur peut de nouveau le modifier / le ré-associer.
     */
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Accord a
            WHERE (a.logementAId = :logementId OR a.logementBId = :logementId)
            AND a.statut IN ('EN_ATTENTE', 'ACCEPTE', 'EN_COURS', 'LITIGE')
            """)
    boolean existsLivingAccordForLogement(@Param("logementId") UUID logementId);

    /**
     * Expire tous les accords EN_ATTENTE créés avant la limite de temps donnée.
     * Retourne le nombre d'accords modifiés.
     *
     * Requête NATIVE avec CAST explicite : le @ColumnTransformer de l'entité
     * ne s'applique pas aux UPDATE en masse JPQL, donc Hibernate enverrait le
     * statut comme varchar et PostgreSQL (colonne de type enum accord_statut)
     * rejetterait la requête. Le CAST garantit le bon type (corrigé APP-115).
     */
    @Modifying
    @Query(value = """
            UPDATE accords
            SET statut = CAST(:nouveauStatut AS accord_statut), updated_at = NOW()
            WHERE statut = 'EN_ATTENTE'
            AND created_at < :limite
            """, nativeQuery = true)
    int expireAccordsEnAttente(
            @Param("limite") OffsetDateTime limite,
            @Param("nouveauStatut") String nouveauStatut
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
