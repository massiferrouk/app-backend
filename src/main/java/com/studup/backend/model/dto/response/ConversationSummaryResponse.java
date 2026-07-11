package com.studup.backend.model.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Résumé d'une conversation pour la liste de la messagerie mobile (APP-75).
 * partnerName est abrégé ("Thomas D.") — pas de nom complet dans les listes.
 */
public record ConversationSummaryResponse(
        UUID conversationId,
        UUID partnerId,
        String partnerName,
        String lastMessage,
        OffsetDateTime lastMessageAt,
        long unreadCount
) {}
