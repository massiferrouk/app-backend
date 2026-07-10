package com.studup.backend.model.entity;

import com.studup.backend.model.enums.ReviewTargetType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Auteur de l'avis — doit faire partie de l'accord
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    // Cible utilisateur (nullable si l'avis cible un logement)
    @Column(name = "target_user_id")
    private UUID targetUserId;

    // Cible logement (nullable si l'avis cible un utilisateur)
    @Column(name = "target_logement_id")
    private UUID targetLogementId;

    // Accord auquel est rattaché l'avis — obligatoire
    @Column(name = "accord_id", nullable = false)
    private UUID accordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, columnDefinition = "review_target_type")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS review_target_type)")
    private ReviewTargetType targetType;

    // Note entre 1 et 5 — SMALLINT en BDD (int2), Integer en Java.
    // @JdbcTypeCode aligne le type JDBC attendu par Hibernate sur le
    // SMALLINT réel de la colonne (sinon la validation de schéma échoue).
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false, columnDefinition = "SMALLINT")
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    // true = signalé par un utilisateur, en attente de décision admin
    @Column(name = "is_reported", nullable = false)
    private Boolean isReported;

    // false = visible | true = masqué par la modération
    @Column(name = "is_moderated", nullable = false)
    private Boolean isModerated;

    @Column(name = "moderation_note")
    private String moderationNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (isReported == null) isReported = false;
        if (isModerated == null) isModerated = false;
    }
}
