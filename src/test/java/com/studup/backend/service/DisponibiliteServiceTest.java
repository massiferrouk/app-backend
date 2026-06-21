package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateDisponibiliteRequest;
import com.studup.backend.model.dto.response.DisponibiliteResponse;
import com.studup.backend.model.entity.Disponibilite;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.DisponibiliteType;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.repository.DisponibiliteRepository;
import com.studup.backend.repository.LogementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisponibiliteServiceTest {

    @Mock private DisponibiliteRepository disponibiliteRepository;
    @Mock private LogementRepository logementRepository;

    @InjectMocks
    private DisponibiliteService disponibiliteService;

    private Logement fakeLogement;

    @BeforeEach
    void setUp() {
        fakeLogement = Logement.builder()
                .id(UUID.randomUUID())
                .adresse("12 rue de la Paix")
                .ville("Paris")
                .codePostal("75001")
                .type(LogementType.STUDIO)
                .surface(new BigDecimal("25.00"))
                .loyer(new BigDecimal("800.00"))
                .charges(new BigDecimal("50.00"))
                .statut(LogementStatut.ACTIF)
                .isVerified(false)
                .isMeuble(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── Création ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreateDisponibiliteWithDefaultTypeLibre() {
        // type non précisé → doit être LIBRE par défaut
        CreateDisponibiliteRequest request = new CreateDisponibiliteRequest(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null
        );

        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(disponibiliteRepository.existsOverlap(any(), any(), any())).thenReturn(false);
        when(disponibiliteRepository.save(any(Disponibilite.class))).thenAnswer(inv -> {
            Disponibilite d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DisponibiliteResponse response = disponibiliteService.create(fakeLogement.getId(), request);

        assertThat(response.type()).isEqualTo(DisponibiliteType.LIBRE);
        assertThat(response.dateDebut()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.dateFin()).isEqualTo(LocalDate.of(2026, 7, 31));
        verify(disponibiliteRepository).save(any());
    }

    @Test
    void shouldCreateDisponibiliteWithExplicitTypeOccupe() {
        CreateDisponibiliteRequest request = new CreateDisponibiliteRequest(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15),
                DisponibiliteType.OCCUPE
        );

        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(disponibiliteRepository.existsOverlap(any(), any(), any())).thenReturn(false);
        when(disponibiliteRepository.save(any(Disponibilite.class))).thenAnswer(inv -> {
            Disponibilite d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DisponibiliteResponse response = disponibiliteService.create(fakeLogement.getId(), request);

        assertThat(response.type()).isEqualTo(DisponibiliteType.OCCUPE);
    }

    @Test
    void shouldThrowWhenLogementNotFound() {
        when(logementRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disponibiliteService.create(UUID.randomUUID(),
                new CreateDisponibiliteRequest(LocalDate.now(), LocalDate.now().plusDays(7), null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Logement introuvable");
    }

    @Test
    void shouldThrowWhenDateDebutAfterDateFin() {
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));

        // dateDebut = dateFin → invalide (doit être strictement avant)
        CreateDisponibiliteRequest request = new CreateDisponibiliteRequest(
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 7, 1),
                null
        );

        assertThatThrownBy(() -> disponibiliteService.create(fakeLogement.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("antérieure");

        verify(disponibiliteRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenOverlapDetected() {
        when(logementRepository.findById(fakeLogement.getId())).thenReturn(Optional.of(fakeLogement));
        when(disponibiliteRepository.existsOverlap(any(), any(), any())).thenReturn(true);

        CreateDisponibiliteRequest request = new CreateDisponibiliteRequest(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null
        );

        assertThatThrownBy(() -> disponibiliteService.create(fakeLogement.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chevauche");

        verify(disponibiliteRepository, never()).save(any());
    }

    // ─── Lecture ──────────────────────────────────────────────────────────────

    @Test
    void shouldReturnDisponibilitesOrderedByDateDebut() {
        Disponibilite d1 = Disponibilite.builder()
                .id(UUID.randomUUID()).logement(fakeLogement)
                .dateDebut(LocalDate.of(2026, 7, 1))
                .dateFin(LocalDate.of(2026, 7, 15))
                .type(DisponibiliteType.LIBRE).build();

        Disponibilite d2 = Disponibilite.builder()
                .id(UUID.randomUUID()).logement(fakeLogement)
                .dateDebut(LocalDate.of(2026, 8, 1))
                .dateFin(LocalDate.of(2026, 8, 31))
                .type(DisponibiliteType.BLOQUE).build();

        when(logementRepository.existsById(fakeLogement.getId())).thenReturn(true);
        when(disponibiliteRepository.findByLogementIdOrderByDateDebutAsc(fakeLogement.getId()))
                .thenReturn(List.of(d1, d2));

        List<DisponibiliteResponse> result = disponibiliteService.findByLogement(fakeLogement.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).dateDebut()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.get(1).type()).isEqualTo(DisponibiliteType.BLOQUE);
    }

    @Test
    void shouldThrowWhenLogementNotFoundOnList() {
        when(logementRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> disponibiliteService.findByLogement(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
