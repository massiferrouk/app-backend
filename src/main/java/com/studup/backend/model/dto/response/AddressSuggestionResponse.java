package com.studup.backend.model.dto.response;

import java.math.BigDecimal;

/**
 * Suggestion d'adresse pour l'autocomplétion du formulaire logement.
 * Renseignée depuis la Base Adresse Nationale (api-adresse.data.gouv.fr).
 */
public record AddressSuggestionResponse(
        String label,       // libellé complet : "8 Boulevard du Port 80000 Amiens"
        String adresse,     // numéro + voie : "8 Boulevard du Port"
        String ville,       // commune : "Amiens"
        String codePostal,  // "80000"
        BigDecimal lat,
        BigDecimal lng
) {}
