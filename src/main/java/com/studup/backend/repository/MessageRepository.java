package com.studup.backend.repository;

import com.studup.backend.model.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    // Historique paginé d'une conversation, du plus récent au plus ancien
    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    // Compte les messages non lus destinés à un utilisateur (envoyés par quelqu'un d'autre)
    @Query("""
            SELECT COUNT(m) FROM Message m
            JOIN ConversationParticipant cp ON cp.conversationId = m.conversationId
            WHERE cp.userId = :userId
            AND m.senderId != :userId
            AND m.isRead = false
            """)
    long countUnreadForUser(@Param("userId") UUID userId);
}
