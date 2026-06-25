package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.ReviewTargetType;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record ReviewRequest(

        @NotNull
        UUID accordId,

        @NotNull
        ReviewTargetType targetType,

        // Obligatoire si targetType = USER
        UUID targetUserId,

        // Obligatoire si targetType = LOGEMENT
        UUID targetLogementId,

        @NotNull
        @Min(1) @Max(5)
        Integer rating,

        @Size(max = 500)
        String comment
) {}
