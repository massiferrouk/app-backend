package com.studup.backend.model.entity;

import com.studup.backend.model.enums.PremiereSemaine;
import com.studup.backend.model.enums.RythmeAlternance;
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
@Table(name = "alternant_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlternantProfile {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Relation vers l'utilisateur — un seul profil alternant par compte
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "ville_a", nullable = false, length = 100)
    private String villeA;

    @Column(name = "ville_b", nullable = false, length = 100)
    private String villeB;

    @Column(nullable = false, length = 200)
    private String ecole;

    @Column(nullable = false, length = 200)
    private String entreprise;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "rythme_alternance")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS rythme_alternance)")
    private RythmeAlternance rythme;

    // Ordre de départ du cycle : première semaine école ou entreprise (APP-110)
    @Enumerated(EnumType.STRING)
    @Column(name = "premiere_semaine", nullable = false, columnDefinition = "premiere_semaine")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS premiere_semaine)")
    private PremiereSemaine premiereSemaine;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
