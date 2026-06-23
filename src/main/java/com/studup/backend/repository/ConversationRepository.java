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

    // Trouve la conversation entre deux utilisateurs (via leurs participations)
    @Query("""
            SELECT c FROM Conversation c
            WHERE EXISTS (
                SELECT p FROM ConversationParticipant p
                WHERE p.conversationId = c.id AND p.userId = :userId1
            )
            AND EXISTS (
                SELECT p FROM ConversationParticipant p
                WHERE p.conversationId = c.id AND p.userId = :userId2
            )
            """)
    Optional<Conversation> findByParticipants(
            @Param("userId1") UUID userId1,
            @Param("userId2") UUID userId2
    );
}
