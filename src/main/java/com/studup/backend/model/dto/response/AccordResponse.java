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
        OffsetDateTime updatedAt
) {
    public static AccordResponse from(Accord accord) {
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
                accord.getUpdatedAt()
        );
    }
}
