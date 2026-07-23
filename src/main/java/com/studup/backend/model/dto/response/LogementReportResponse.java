package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.LogementReport;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un signalement d'annonce, tel que le voit un modérateur (APP-121).
 *
 * Même principe que MessageReportResponse : la réponse porte le contexte
 * nécessaire à la décision, pas seulement des identifiants. Un modérateur doit
 * savoir de quelle annonce il s'agit et qui l'a signalée sans ouvrir trois
 * écrans.
 */
public record LogementReportResponse(
        UUID id,
        UUID logementId,
        String motif,
        OffsetDateTime createdAt,

        /** Null si l'annonce a disparu entre le signalement et la consultation */
        String logementLibelle,
        String proprietaire,
        String signalePar
) {
    public static LogementReportResponse withContexte(LogementReport report,
                                                      String logementLibelle,
                                                      String proprietaire,
                                                      String signalePar) {
        return new LogementReportResponse(
                report.getId(),
                report.getLogementId(),
                report.getMotif(),
                report.getCreatedAt(),
                logementLibelle,
                proprietaire,
                signalePar
        );
    }
}
