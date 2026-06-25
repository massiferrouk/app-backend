package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.NotificationPreference;
import com.studup.backend.model.enums.NotificationChannel;
import com.studup.backend.model.enums.NotificationType;

import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID id,
        NotificationType notificationType,
        NotificationChannel channel,
        Boolean isEnabled
) {
    public static NotificationPreferenceResponse from(NotificationPreference p) {
        return new NotificationPreferenceResponse(
                p.getId(), p.getNotificationType(), p.getChannel(), p.getIsEnabled());
    }
}
