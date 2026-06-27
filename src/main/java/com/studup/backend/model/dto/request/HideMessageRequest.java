package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record HideMessageRequest(
        @NotBlank(message = "La note de modération est obligatoire")
        String moderationNote
) {}
