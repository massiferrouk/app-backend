package com.studup.backend.model.dto.response;

import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;

import java.time.LocalDate;
import java.util.UUID;

public record AccordSummaryResponse(
        UUID id,
        AccordType type,
        AccordStatut statut,
        LocalDate dateDebut,
        LocalDate dateFin,
        UUID partnerId,
        Long heuresAvantExpiration
) {}
