package com.studup.backend.model.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Résumé d'une conversation pour la liste de la messagerie mobile (APP-75).
 * partnerName est abrégé ("Thomas D.") — pas de nom complet dans les listes.
 *
 * Les champs logement* (APP-119) identifient l'annonce sur laquelle porte la
 * discussion, pour que l'utilisateur sache de quel bien on parle quand un
 * propriétaire en publie plusieurs. Tous null = discussion de personne à
 * personne (mise en relation alternant ↔ alternant).
 */
public record ConversationSummaryResponse(
        UUID conversationId,
        UUID partnerId,
        String partnerName,
        String lastMessage,
        OffsetDateTime lastMessageAt,
        long unreadCount,
        UUID logementId,
        String logementVille,
        String logementType
) {}
