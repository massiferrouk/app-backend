package com.studup.backend.event;

import com.studup.backend.model.entity.Review;

/**
 * Événement publié par ReviewService après la création d'un avis.
 * ReputationService écoute cet événement pour recalculer le score de la cible.
 */
public record ReviewCreatedEvent(Review review) {}
