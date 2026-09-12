package com.studup.backend.model.dto.response;

import com.studup.backend.model.enums.LogementType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Résumé léger du logement d'un match, affiché sur les cartes de l'écran
 * Matches (APP-122) : de quoi montrer une vignette + une ligne d'info sans avoir
 * à ouvrir la fiche. {@code photoUrl} est l'URL signée de la photo de couverture
 * (la première), ou null si le logement n'a aucune photo.
 */
public record LogementApercuResponse(
        UUID id,
        String ville,
        LogementType type,
        BigDecimal loyer,
        String photoUrl
) {
}
