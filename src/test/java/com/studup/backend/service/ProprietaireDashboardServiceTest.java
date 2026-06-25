package com.studup.backend.service;

import com.studup.backend.model.dto.response.ProprietaireDashboardResponse;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProprietaireDashboardServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private LogementRepository logementRepository;
    @Mock private AccordRepository accordRepository;

    @InjectMocks private ProprietaireDashboardService service;

    private User proprio;

    @BeforeEach
    void setUp() {
        proprio = User.builder()
                .id(UUID.randomUUID()).email("proprio@studup.fr")
                .firstName("Marc").lastName("P").role(UserRole.PROPRIETAIRE)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    private Logement logement(UUID id, LogementStatut statut) {
        return Logement.builder()
                .id(id).ville("Paris").adresse("12 rue de la Paix").codePostal("75001")
                .type(LogementType.STUDIO).statut(statut).loyer(BigDecimal.valueOf(800))
                .nbPieces(1).isVerified(false).isMeuble(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    // ─── cas nominal : 2 logements actifs, 1 occupé ──────────────────────────

    @Test
    void shouldCalculateOccupancyRate() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Logement l1 = logement(id1, LogementStatut.ACTIF);
        Logement l2 = logement(id2, LogementStatut.ACTIF);

        when(userRepository.findByEmail("proprio@studup.fr")).thenReturn(Optional.of(proprio));
        when(logementRepository.findByOwnerId(proprio.getId())).thenReturn(List.of(l1, l2));
        // l1 est occupé, l2 est libre
        when(accordRepository.findOccupiedLogementIds(any())).thenReturn(List.of(id1));

        ProprietaireDashboardResponse result = service.getDashboard("proprio@studup.fr");

        assertThat(result.nbLogementsTotaux()).isEqualTo(2);
        assertThat(result.nbLogementsActifs()).isEqualTo(2);
        assertThat(result.nbLocatairesActifs()).isEqualTo(1);
        assertThat(result.tauxOccupation()).isEqualTo(50.0);
        assertThat(result.logements()).hasSize(2);
        assertThat(result.logements().stream().filter(l -> l.id().equals(id1)).findFirst().get().isOccupe()).isTrue();
        assertThat(result.logements().stream().filter(l -> l.id().equals(id2)).findFirst().get().isOccupe()).isFalse();
    }

    // ─── 0 logement actif → taux = 0 (pas de division par zéro) ─────────────

    @Test
    void shouldReturnZeroOccupancyWhenNoActiveLogement() {
        UUID id1 = UUID.randomUUID();
        Logement brouillon = logement(id1, LogementStatut.BROUILLON);

        when(userRepository.findByEmail("proprio@studup.fr")).thenReturn(Optional.of(proprio));
        when(logementRepository.findByOwnerId(proprio.getId())).thenReturn(List.of(brouillon));
        when(accordRepository.findOccupiedLogementIds(any())).thenReturn(List.of());

        ProprietaireDashboardResponse result = service.getDashboard("proprio@studup.fr");

        assertThat(result.nbLogementsActifs()).isEqualTo(0);
        assertThat(result.tauxOccupation()).isEqualTo(0.0);
    }

    // ─── aucun logement → dashboard vide cohérent ────────────────────────────

    @Test
    void shouldReturnEmptyDashboardWhenNoLogements() {
        when(userRepository.findByEmail("proprio@studup.fr")).thenReturn(Optional.of(proprio));
        when(logementRepository.findByOwnerId(proprio.getId())).thenReturn(List.of());

        ProprietaireDashboardResponse result = service.getDashboard("proprio@studup.fr");

        assertThat(result.nbLogementsTotaux()).isEqualTo(0);
        assertThat(result.nbLocatairesActifs()).isEqualTo(0);
        assertThat(result.tauxOccupation()).isEqualTo(0.0);
        assertThat(result.logements()).isEmpty();
    }

    // ─── tous les logements actifs et tous occupés → taux = 100 ─────────────

    @Test
    void shouldReturnFullOccupancyWhenAllOccupied() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(userRepository.findByEmail("proprio@studup.fr")).thenReturn(Optional.of(proprio));
        when(logementRepository.findByOwnerId(proprio.getId())).thenReturn(List.of(
                logement(id1, LogementStatut.ACTIF),
                logement(id2, LogementStatut.ACTIF)));
        when(accordRepository.findOccupiedLogementIds(any())).thenReturn(List.of(id1, id2));

        ProprietaireDashboardResponse result = service.getDashboard("proprio@studup.fr");

        assertThat(result.tauxOccupation()).isEqualTo(100.0);
        assertThat(result.nbLocatairesActifs()).isEqualTo(2);
    }
}
