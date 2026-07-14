package com.studup.backend.model.entity;

import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.model.enums.VilleAssociee;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "logements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Logement {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 500)
    private String adresse;

    @Column(nullable = false, length = 100)
    private String ville;

    @Column(name = "code_postal", nullable = false, length = 10)
    private String codePostal;

    // Coordonnées géographiques remplies par Nominatim après création
    @Column(precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(precision = 9, scale = 6)
    private BigDecimal lng;

    // Mapping natif de l'ENUM PostgreSQL (Hibernate 6). Contrairement à
    // @Enumerated(STRING)+@ColumnTransformer, NAMED_ENUM lie le paramètre comme
    // le type enum natif : les comparaisons SQL (WHERE type = ?) fonctionnent
    // (sinon erreur 42883 — cf. APP-91 / recherche de logements).
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "logement_type")
    private LogementType type;

    @Column(precision = 6, scale = 2)
    private BigDecimal surface;

    @Column(name = "nb_pieces", nullable = false)
    private Integer nbPieces;

    @Column(precision = 8, scale = 2)
    private BigDecimal loyer;

    @Column(precision = 8, scale = 2)
    private BigDecimal charges;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Tableau PostgreSQL natif : {"wifi","parking","lave-linge"}
    @Column(columnDefinition = "TEXT[]")
    private String[] equipements;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "logement_statut")
    private LogementStatut statut;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    @Column(name = "is_meuble", nullable = false)
    private Boolean isMeuble;

    // Nullable : renseigné uniquement quand l'alternant associe son logement à une de ses villes
    @Enumerated(EnumType.STRING)
    @Column(name = "ville_associee", columnDefinition = "ville_associee")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS ville_associee)")
    private VilleAssociee villeAssociee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (statut == null) statut = LogementStatut.BROUILLON;
        if (isVerified == null) isVerified = false;
        if (isMeuble == null) isMeuble = true;
        if (nbPieces == null) nbPieces = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
