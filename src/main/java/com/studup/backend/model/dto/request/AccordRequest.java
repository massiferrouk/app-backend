package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.AccordType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccordRequest(

        @NotNull(message = "Le destinataire est obligatoire")
        UUID receiverId,

        @NotNull(message = "Le type d'accord est obligatoire")
        AccordType type,

        // Dates OPTIONNELLES : par défaut l'accord couvre toute la période
        // commune des deux alternances, calculée par le backend. L'utilisateur
        // ne saisit pas de dates — l'app se contente de mettre en relation,
        // l'organisation reste à la main des deux utilisateurs.
        LocalDate dateDebut,
        LocalDate dateFin,

        UUID logementAId,
        UUID logementBId,
        BigDecimal montantLoyer,
        String messageInitial
) {}
