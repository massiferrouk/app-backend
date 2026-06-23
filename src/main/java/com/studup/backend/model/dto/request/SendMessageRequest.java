package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Le contenu du message est obligatoire")
        @Size(max = 2000, message = "Le message ne peut pas dépasser 2000 caractères")
        String content
) {}
