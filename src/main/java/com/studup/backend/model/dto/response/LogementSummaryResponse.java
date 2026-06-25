package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;

import java.math.BigDecimal;
import java.util.UUID;

public record LogementSummaryResponse(
        UUID id,
        String ville,
        String adresse,
        LogementType type,
        LogementStatut statut,
        BigDecimal loyer,
        boolean isOccupe
) {
    public static LogementSummaryResponse from(Logement l, boolean isOccupe) {
        return new LogementSummaryResponse(
                l.getId(),
                l.getVille(),
                l.getAdresse(),
                l.getType(),
                l.getStatut(),
                l.getLoyer(),
                isOccupe
        );
    }
}
