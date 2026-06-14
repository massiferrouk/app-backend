package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProprietaireProfileRequest(

        @NotBlank(message = "Le téléphone est obligatoire")
        @Pattern(
                regexp = "^(\\+33|0033|0)[1-9](\\d{2}){4}$",
                message = "Le numéro de téléphone doit être un numéro français valide"
        )
        String phone,

        @NotBlank(message = "L'adresse est obligatoire")
        @Size(max = 255, message = "L'adresse ne peut pas dépasser 255 caractères")
        String adresse,

        @NotBlank(message = "La ville est obligatoire")
        @Size(max = 100, message = "La ville ne peut pas dépasser 100 caractères")
        String ville,

        @NotBlank(message = "Le code postal est obligatoire")
        @Pattern(
                regexp = "^[0-9]{5}$",
                message = "Le code postal doit contenir exactement 5 chiffres"
        )
        String codePostal,

        @Size(min = 14, max = 14, message = "Le SIRET doit contenir exactement 14 chiffres")
        @Pattern(
                regexp = "^[0-9]{14}$",
                message = "Le SIRET doit contenir uniquement des chiffres"
        )
        String siret
) {}
