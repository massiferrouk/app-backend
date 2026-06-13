package com.studup.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alternance_schedules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlternanceSchedule {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Relation vers le profil alternant — une semaine appartient à un seul profil
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private AlternantProfile profile;

    // Toujours un lundi — représente la semaine entière
    @Column(nullable = false)
    private LocalDate semaine;

    // 'A' = ville école | 'B' = ville entreprise
    @Column(nullable = false, length = 1)
    private String label;

    // false = généré automatiquement | true = modifié manuellement par l'alternant
    @Column(name = "is_overridden", nullable = false)
    private Boolean isOverridden;

    @Column(name = "override_reason", length = 200)
    private String overrideReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (isOverridden == null) isOverridden = false;
    }
}
