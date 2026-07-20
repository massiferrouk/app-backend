package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.Candidature;
import com.studup.backend.model.enums.CandidatureStatut;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Une candidature + l'annonce concernée.
 * On embarque un LogementResponse complet : le client réutilise ainsi son
 * modèle Logement existant, sans DTO ni parsing supplémentaire.
 */
public record CandidatureResponse(
        UUID id,
        CandidatureStatut statut,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        LogementResponse logement
) {
    public static CandidatureResponse from(Candidature c, List<String> photoUrls) {
        return new CandidatureResponse(
                c.getId(),
                c.getStatut(),
                c.getNote(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                LogementResponse.from(c.getLogement(), photoUrls)
        );
    }
}
