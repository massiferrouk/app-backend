package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.Disponibilite;
import com.studup.backend.model.enums.DisponibiliteType;

import java.time.LocalDate;
import java.util.UUID;

public record DisponibiliteResponse(
        UUID id,
        UUID logementId,
        LocalDate dateDebut,
        LocalDate dateFin,
        DisponibiliteType type
) {
    public static DisponibiliteResponse from(Disponibilite d) {
        return new DisponibiliteResponse(
                d.getId(),
                d.getLogement().getId(),
                d.getDateDebut(),
                d.getDateFin(),
                d.getType()
        );
    }
}
