package com.studup.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "photos_logements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoLogement {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logement_id", nullable = false)
    private Logement logement;

    // Clé MinIO : UUID généré côté serveur, jamais le nom original du fichier
    @Column(name = "file_key", nullable = false, unique = true, length = 500)
    private String fileKey;

    // Ordre d'affichage : 0 = photo principale
    @Column(nullable = false)
    private Integer ordre;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (ordre == null) ordre = 0;
    }
}
