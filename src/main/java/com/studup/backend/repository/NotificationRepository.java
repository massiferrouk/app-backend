package com.studup.backend.repository;

import com.studup.backend.model.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId);

    /**
     * Une notification ANNONCE_SUIVIE existe-t-elle déjà pour ce couple
     * (étudiant, annonce) chez ce propriétaire ? (APP-119)
     * Empêche le spam « retirer / re-suivre » : chaque étudiant ne déclenche
     * qu'une seule alerte par annonce, mais chaque nouvel étudiant en
     * déclenche bien une.
     *
     * Requête native : le filtre porte sur le JSONB (payload ->> 'clé') et la
     * colonne type est un enum PostgreSQL, comparé via type::text (même piège
     * que le bug accord_statut : jamais de paramètre lié directement à un enum).
     */
    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM notifications
                WHERE user_id = :userId
                  AND type::text = 'ANNONCE_SUIVIE'
                  AND payload ->> 'logementId' = :logementId
                  AND payload ->> 'etudiantId' = :etudiantId
            )
            """, nativeQuery = true)
    boolean existsAnnonceSuivie(@Param("userId") UUID userId,
                                @Param("logementId") String logementId,
                                @Param("etudiantId") String etudiantId);
}
