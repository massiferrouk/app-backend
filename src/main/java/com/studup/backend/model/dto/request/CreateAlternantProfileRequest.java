package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.RythmeAlternance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateAlternantProfileRequest(

        @NotBlank(message = "La ville A est obligatoire")
        @Size(max = 100, message = "La ville A ne peut pas dépasser 100 caractères")
        String villeA,

        @NotBlank(message = "La ville B est obligatoire")
        @Size(max = 100, message = "La ville B ne peut pas dépasser 100 caractères")
        String villeB,

        @NotBlank(message = "L'école est obligatoire")
        @Size(max = 200, message = "Le nom de l'école ne peut pas dépasser 200 caractères")
        String ecole,

        @NotBlank(message = "L'entreprise est obligatoire")
        @Size(max = 200, message = "Le nom de l'entreprise ne peut pas dépasser 200 caractères")
        String entreprise,

        @NotNull(message = "La date de début est obligatoire")
        LocalDate dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDate dateFin,

        @NotNull(message = "Le rythme d'alternance est obligatoire")
        RythmeAlternance rythme
) {}
