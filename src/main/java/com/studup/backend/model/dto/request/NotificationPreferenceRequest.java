package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.NotificationChannel;
import com.studup.backend.model.enums.NotificationType;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceRequest(
        @NotNull NotificationType notificationType,
        @NotNull NotificationChannel channel,
        @NotNull Boolean enabled
) {}
