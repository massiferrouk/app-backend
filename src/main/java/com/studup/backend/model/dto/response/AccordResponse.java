package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccordResponse(
        UUID id,
        UUID initiatorId,
        UUID receiverId,
        UUID logementAId,
        UUID logementBId,
        AccordType type,
        AccordStatut statut,
        LocalDate dateDebut,
        LocalDate dateFin,
        BigDecimal montantLoyer,
        String messageInitial,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        // Prénoms des participants — permettent au frontend d'afficher le nom
        // du partenaire (ex: bouton « Contacter ») sans appel supplémentaire.
        // null quand la réponse est construite sans contexte utilisateur.
        String initiatorPrenom,
        String receiverPrenom
) {
    public static AccordResponse from(Accord accord) {
        return from(accord, null, null);
    }

    public static AccordResponse from(Accord accord, String initiatorPrenom, String receiverPrenom) {
        return new AccordResponse(
                accord.getId(),
                accord.getInitiatorId(),
                accord.getReceiverId(),
                accord.getLogementAId(),
                accord.getLogementBId(),
                accord.getType(),
                accord.getStatut(),
                accord.getDateDebut(),
                accord.getDateFin(),
                accord.getMontantLoyer(),
                accord.getMessageInitial(),
                accord.getCreatedAt(),
                accord.getUpdatedAt(),
                initiatorPrenom,
                receiverPrenom
        );
    }
}
