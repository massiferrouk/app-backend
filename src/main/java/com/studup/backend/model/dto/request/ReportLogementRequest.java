package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Signalement d'une annonce par un utilisateur (APP-121). */
public record ReportLogementRequest(
        @NotBlank(message = "Le motif de signalement est obligatoire")
        @Size(max = 500, message = "Le motif ne peut pas dépasser 500 caractères")
        String motif
) {}
