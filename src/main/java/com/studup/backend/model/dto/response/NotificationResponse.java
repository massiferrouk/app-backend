package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.Notification;
import com.studup.backend.model.enums.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        Boolean isRead,
        String deepLink,
        OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(),
                n.getBody(), n.getIsRead(), n.getDeepLink(), n.getCreatedAt()
        );
    }
}
