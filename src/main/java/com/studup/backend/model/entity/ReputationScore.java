package com.studup.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reputation_scores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReputationScore {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Un seul score par utilisateur — contrainte UNIQUE en base (V10)
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    // Moyenne pondérée des avis — entre 0.00 et 5.00
    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "total_reviews")
    private Integer totalReviews;

    // Score moyen sur les logements uniquement
    @Column(name = "logement_score", precision = 3, scale = 2)
    private BigDecimal logementScore;

    // Nombre d'accords terminés — utilisé pour les badges
    @Column(name = "nb_accords")
    private Integer nbAccords;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
        if (totalReviews == null) totalReviews = 0;
        if (nbAccords == null) nbAccords = 0;
    }
}
