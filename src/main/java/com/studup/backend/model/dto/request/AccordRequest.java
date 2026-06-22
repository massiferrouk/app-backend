package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.AccordType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccordRequest(

        @NotNull(message = "Le destinataire est obligatoire")
        UUID receiverId,

        @NotNull(message = "Le type d'accord est obligatoire")
        AccordType type,

        @NotNull(message = "La date de début est obligatoire")
        @Future(message = "La date de début doit être dans le futur")
        LocalDate dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDate dateFin,

        UUID logementAId,
        UUID logementBId,
        BigDecimal montantLoyer,
        String messageInitial
) {}
