package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.MessageReport;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un signalement de message, tel que le voit un modérateur.
 *
 * APP-121 : la réponse ne portait que des identifiants. Un modérateur devait
 * donc décider de masquer un message sans pouvoir le lire — aucun endpoint ne
 * le lui donnait, GET /messages/{conversationId} étant réservé aux
 * participants. Le contenu, son auteur et le signaleur sont désormais joints.
 *
 * Le nom du signaleur est volontairement exposé : sans lui, impossible de
 * repérer quelqu'un qui signalerait systématiquement la même personne.
 */
public record MessageReportResponse(
        UUID id,
        UUID messageId,
        UUID reporterId,
        String motif,
        OffsetDateTime createdAt,

        /** Contenu signalé — null si le message a disparu entre-temps */
        String contenuMessage,
        UUID auteurId,
        String auteurNom,
        OffsetDateTime messageCreeLe,
        String signalePar
) {
    /** Version sans contexte — utilisée à la création d'un signalement. */
    public static MessageReportResponse from(MessageReport report) {
        return new MessageReportResponse(
                report.getId(),
                report.getMessageId(),
                report.getReporterId(),
                report.getMotif(),
                report.getCreatedAt(),
                null, null, null, null, null
        );
    }

    /** Version enrichie — utilisée dans la file de modération. */
    public static MessageReportResponse withContexte(MessageReport report,
                                                     String contenuMessage,
                                                     UUID auteurId,
                                                     String auteurNom,
                                                     OffsetDateTime messageCreeLe,
                                                     String signalePar) {
        return new MessageReportResponse(
                report.getId(),
                report.getMessageId(),
                report.getReporterId(),
                report.getMotif(),
                report.getCreatedAt(),
                contenuMessage,
                auteurId,
                auteurNom,
                messageCreeLe,
                signalePar
        );
    }
}
