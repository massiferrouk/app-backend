package com.studup.backend.model.entity;

import com.studup.backend.model.enums.CandidatureStatut;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une annonce suivie par un utilisateur, avec son statut de candidature.
 * Contrainte d'unicité (user, logement) côté base : on ne suit pas deux fois
 * la même annonce.
 */
@Entity
@Table(name = "candidatures")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candidature {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    // L'utilisateur qui suit l'annonce (l'étudiant en recherche)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logement_id", nullable = false)
    private Logement logement;

    // Enum natif PostgreSQL (candidature_statut) : le CAST est indispensable en
    // écriture, sinon Hibernate envoie un varchar et PostgreSQL refuse.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "candidature_statut")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS candidature_statut)")
    private CandidatureStatut statut;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (statut == null) statut = CandidatureStatut.A_CONTACTER;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
