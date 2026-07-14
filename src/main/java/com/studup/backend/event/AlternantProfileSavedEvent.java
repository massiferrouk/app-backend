package com.studup.backend.event;

import java.util.UUID;

/**
 * Publié par AlternantProfileService après création ou modification d'un
 * profil alternant. MatchNotificationService l'écoute pour calculer les
 * nouveaux matchs et notifier les alternants compatibles (APP-98).
 */
public record AlternantProfileSavedEvent(UUID userId) {}
