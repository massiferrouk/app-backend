package com.yuniv.backend.model.dto.response;

/**
 * Réponse retournée après un login ou un refresh réussi.
 * Contient les deux tokens que Flutter stocke côté client.
 */
public record AuthResponse(

        // Token de courte durée (15 min) — envoyé dans chaque requête API
        String accessToken,

        // Token de longue durée (7 jours) — utilisé uniquement pour obtenir un nouvel accessToken
        String refreshToken
) {}
