package com.studup.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "jours_feries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourFerie {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "date_jour", nullable = false)
    private LocalDate dateJour;

    @Column(nullable = false, length = 100)
    private String libelle;

    // Code pays ISO-3166 — 'FR' par défaut
    @Column(nullable = false, length = 2)
    private String pays;

    // Null = jour férié national | non null = spécifique à une région (ex: Alsace-Moselle)
    @Column(length = 50)
    private String region;
}
