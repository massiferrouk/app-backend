package com.studup.backend.model.entity;

import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accords")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Accord {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(name = "logement_a_id")
    private UUID logementAId;

    @Column(name = "logement_b_id")
    private UUID logementBId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "accord_type")
    private AccordType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "accord_statut")
    private AccordStatut statut;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "montant_loyer", precision = 8, scale = 2)
    private BigDecimal montantLoyer;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "message_initial", columnDefinition = "TEXT")
    private String messageInitial;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
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
