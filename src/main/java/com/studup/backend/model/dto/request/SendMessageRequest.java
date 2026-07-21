package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotBlank(message = "Le contenu du message est obligatoire")
        @Size(max = 2000, message = "Le message ne peut pas dépasser 2000 caractères")
        String content,

        /**
         * Annonce sur laquelle porte la discussion (APP-119) — facultatif.
         * Renseigné quand on contacte depuis une annonce : chaque logement a son
         * propre fil. Laissé null pour une mise en relation alternant ↔ alternant,
         * où la discussion porte sur la personne et non sur un logement.
         */
        UUID logementId
) {}
