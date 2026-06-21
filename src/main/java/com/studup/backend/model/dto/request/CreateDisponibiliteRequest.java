package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.DisponibiliteType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateDisponibiliteRequest(

        @NotNull(message = "La date de début est obligatoire")
        LocalDate dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDate dateFin,

        DisponibiliteType type
) {}
