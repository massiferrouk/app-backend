package com.yuniv.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
