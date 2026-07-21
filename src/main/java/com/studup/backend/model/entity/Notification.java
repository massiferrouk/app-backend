package com.studup.backend.model.entity;

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
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "notification_type")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS notification_type)")
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    // Route Flutter pour ouvrir l'écran concerné (ex: "accord/123")
    @Column(name = "deep_link")
    private String deepLink;

    // Données contextuelles JSON (colonne JSONB de V9, mappée depuis APP-119).
    // Sert notamment à dédupliquer : une notification « annonce suivie » porte
    // {"logementId": "...", "etudiantId": "..."} pour ne jamais re-notifier le
    // même couple (étudiant, annonce) si l'étudiant retire puis re-suit.
    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS jsonb)")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (isRead == null) isRead = false;
    }
}
