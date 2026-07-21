package com.studup.backend.repository;

import com.studup.backend.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Conversation entre deux utilisateurs PORTANT SUR UNE ANNONCE précise (APP-119).
     * Deux annonces du même propriétaire = deux fils distincts.
     */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.logementId = :logementId
            AND EXISTS (
                SELECT p FROM ConversationParticipant p
                WHERE p.conversationId = c.id AND p.userId = :userId1
            )
            AND EXISTS (
                SELECT p FROM ConversationParticipant p
                WHERE p.conversationId = c.id AND p.userId = :userId2
            )
            """)
    Optional<Conversation> findByParticipantsAndLogement(
            @Param("userId1") UUID userId1,
            @Param("userId2") UUID userId2,
            @Param("logementId") UUID logementId
    );

    /**
     * Conversation de personne à personne, sans annonce (APP-119) : c'est le cas
     * des mises en relation alternant ↔ alternant issues du matching.
     *
     * Requête séparée plutôt qu'un paramètre nullable : comparer une colonne à un
     * paramètre NULL ne renvoie jamais vrai en SQL (NULL = NULL vaut NULL), il faut
     * un IS NULL explicite.
     */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.logementId IS NULL
            AND EXISTS (
                SELECT p FROM ConversationParticipant p
                WHERE p.conversationId = c.id AND p.userId = :userId1
            )
            AND EXISTS (
                SELECT p FROM ConversationParticipant p
                WHERE p.conversationId = c.id AND p.userId = :userId2
            )
            """)
    Optional<Conversation> findByParticipantsSansLogement(
            @Param("userId1") UUID userId1,
            @Param("userId2") UUID userId2
    );
}
