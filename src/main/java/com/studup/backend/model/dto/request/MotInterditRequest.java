package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ajout d'un mot à la liste filtrée. La colonne fait 100 caractères. */
public record MotInterditRequest(
        @NotBlank(message = "Le mot est obligatoire")
        @Size(max = 100, message = "Le mot ne peut pas dépasser 100 caractères")
        String mot
) {}
