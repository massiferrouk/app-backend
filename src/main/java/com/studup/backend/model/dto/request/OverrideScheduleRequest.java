package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OverrideScheduleRequest(

        // Nouveau label : 'A' (ville école) ou 'B' (ville entreprise)
        @NotBlank
        @Pattern(regexp = "^[AB]$", message = "Le label doit être 'A' ou 'B'")
        String label,

        // Raison obligatoire pour conserver un historique lisible
        @NotNull
        @Pattern(regexp = "^(rattrapage|conges|autre)$",
                message = "La raison doit être 'rattrapage', 'conges' ou 'autre'")
        String reason
) {}
