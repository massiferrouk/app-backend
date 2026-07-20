package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.CandidatureStatut;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Faire évoluer le statut d'une candidature (et sa note perso). */
public record UpdateCandidatureRequest(

        @NotNull(message = "Le statut est obligatoire")
        CandidatureStatut statut,

        @Size(max = 500, message = "La note ne peut pas dépasser 500 caractères")
        String note
) {}
