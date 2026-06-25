package com.studup.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HideReviewRequest(
        @NotBlank @Size(max = 500) String moderationNote
) {}
