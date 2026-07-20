package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.CandidatureStatut;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Suivre une annonce (APP-117).
 * [statut] est optionnel : A_CONTACTER par défaut (bouton « Suivre »),
 * CONTACTE quand l'ajout vient du bouton « Contacter ».
 */
public record CreateCandidatureRequest(

        @NotNull(message = "L'identifiant du logement est obligatoire")
        UUID logementId,

        CandidatureStatut statut
) {}
