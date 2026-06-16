package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.LogementType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateLogementRequest(

        @NotBlank(message = "L'adresse est obligatoire")
        @Size(max = 500)
        String adresse,

        @NotBlank(message = "La ville est obligatoire")
        @Size(max = 100)
        String ville,

        @NotBlank(message = "Le code postal est obligatoire")
        @Pattern(regexp = "\\d{5}", message = "Le code postal doit contenir 5 chiffres")
        String codePostal,

        @NotNull(message = "Le type de logement est obligatoire")
        LogementType type,

        @DecimalMin(value = "1.0", message = "La surface doit être supérieure à 0")
        BigDecimal surface,

        @Min(value = 1)
        Integer nbPieces,

        @DecimalMin(value = "0.0", message = "Le loyer ne peut pas être négatif")
        BigDecimal loyer,

        @DecimalMin(value = "0.0")
        BigDecimal charges,

        @Size(max = 5000)
        String description,

        String[] equipements,

        Boolean isMeuble
) {}
