package com.studup.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Signalement d'une annonce par un utilisateur (APP-121).
 * Un même utilisateur ne peut signaler une annonce qu'une fois — contrainte
 * d'unicité (logement_id, reporter_id) en base.
 */
@Entity
@Table(name = "logement_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogementReport {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "logement_id", nullable = false)
    private UUID logementId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motif;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
