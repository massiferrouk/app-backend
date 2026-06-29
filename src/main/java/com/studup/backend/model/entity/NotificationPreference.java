package com.studup.backend.model.entity;

import com.studup.backend.model.enums.NotificationChannel;
import com.studup.backend.model.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, columnDefinition = "notification_type")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS notification_type)")
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "notification_channel")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS notification_channel)")
    private NotificationChannel channel;

    // true = l'utilisateur veut recevoir ce type sur ce canal
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
        if (isEnabled == null) isEnabled = true;
    }
}
