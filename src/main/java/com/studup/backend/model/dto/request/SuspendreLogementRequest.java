package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Suspension d'une annonce par un administrateur (APP-121).
 * Le motif est obligatoire : il est envoyé au propriétaire pour lui expliquer
 * pourquoi son annonce disparaît.
 */
public record SuspendreLogementRequest(
        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 500, message = "Le motif ne peut pas dépasser 500 caractères")
        String motif
) {}
