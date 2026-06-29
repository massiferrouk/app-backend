package com.studup.backend.model.entity;

import com.studup.backend.model.enums.DisponibiliteType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "disponibilites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disponibilite {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logement_id", nullable = false)
    private Logement logement;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "disponibilite_type")
    @org.hibernate.annotations.ColumnTransformer(write = "CAST(? AS disponibilite_type)")
    @Builder.Default
    private DisponibiliteType type = DisponibiliteType.LIBRE;
}
