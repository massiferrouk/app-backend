package com.studup.backend.service;

import com.studup.backend.model.dto.response.AlternantDashboardResponse;
import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlternantDashboardServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AccordRepository accordRepository;
    @Mock private LogementRepository logementRepository;

    @InjectMocks private AlternantDashboardService service;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = User.builder()
                .id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        bob = User.builder()
                .id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("B").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    private Accord accord(AccordType type, AccordStatut statut, LocalDate debut, LocalDate fin) {
        return Accord.builder()
                .id(UUID.randomUUID())
                .initiatorId(alice.getId())
                .receiverId(bob.getId())
                .type(type).statut(statut)
                .dateDebut(debut).dateFin(fin)
                .createdAt(OffsetDateTime.now()).build();
    }

    private Logement logement(BigDecimal loyer) {
        return Logement.builder()
                .id(UUID.randomUUID()).ville("Paris").adresse("1 rue Test").codePostal("75001")
                .type(LogementType.STUDIO).statut(LogementStatut.ACTIF).loyer(loyer)
                .nbPieces(1).isVerified(false).isMeuble(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    // ─── cas nominal : accord prochain + accord en attente ───────────────────

    @Test
    void shouldReturnDashboardWithProchainAndEnAttente() {
        Accord prochain = accord(AccordType.ECHANGE_TOTAL, AccordStatut.EN_COURS,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(30));
        Accord enAttente = accord(AccordType.ECHANGE_TOTAL, AccordStatut.EN_ATTENTE, null, null);
        enAttente.setReceiverId(alice.getId());
        enAttente.setInitiatorId(bob.getId());

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findProchainAccords(eq(alice.getId()), any())).thenReturn(List.of(prochain));
        when(accordRepository.findAccordsEnAttenteForReceiver(alice.getId())).thenReturn(List.of(enAttente));
        when(accordRepository.findAccordsTerminesEchange(alice.getId())).thenReturn(List.of());

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        assertThat(result.prochainAccords()).hasSize(1);
        assertThat(result.accordsEnAttente()).hasSize(1);
        assertThat(result.accordsEnAttente().get(0).heuresAvantExpiration()).isNotNull();
        assertThat(result.accordsEnAttente().get(0).heuresAvantExpiration()).isLessThanOrEqualTo(72L);
    }

    // ─── calcul économies : 1 accord terminé de 3 mois, loyer 900€ ──────────

    @Test
    void shouldCalculateSavings() {
        LocalDate debut = LocalDate.now().minusMonths(4);
        LocalDate fin = debut.plusMonths(3);
        Accord termine = accord(AccordType.ECHANGE_TOTAL, AccordStatut.TERMINE, debut, fin);

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findProchainAccords(any(), any())).thenReturn(List.of());
        when(accordRepository.findAccordsEnAttenteForReceiver(any())).thenReturn(List.of());
        when(accordRepository.findAccordsTerminesEchange(alice.getId())).thenReturn(List.of(termine));
        when(logementRepository.findByOwnerId(alice.getId()))
                .thenReturn(List.of(logement(BigDecimal.valueOf(900))));

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        // 900€ × 3 mois = 2700€
        assertThat(result.economiesEstimees()).isEqualByComparingTo(BigDecimal.valueOf(2700.00));
        assertThat(result.nbAccordsTermines()).isEqualTo(1);
    }

    // ─── aucun logement → économies = 0 ──────────────────────────────────────

    @Test
    void shouldReturnZeroSavingsWhenNoLogement() {
        Accord termine = accord(AccordType.ECHANGE_TOTAL, AccordStatut.TERMINE,
                LocalDate.now().minusMonths(3), LocalDate.now().minusMonths(1));

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findProchainAccords(any(), any())).thenReturn(List.of());
        when(accordRepository.findAccordsEnAttenteForReceiver(any())).thenReturn(List.of());
        when(accordRepository.findAccordsTerminesEchange(alice.getId())).thenReturn(List.of(termine));
        when(logementRepository.findByOwnerId(alice.getId())).thenReturn(List.of());

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        assertThat(result.economiesEstimees()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── dashboard vide ───────────────────────────────────────────────────────

    @Test
    void shouldReturnEmptyDashboard() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findProchainAccords(any(), any())).thenReturn(List.of());
        when(accordRepository.findAccordsEnAttenteForReceiver(any())).thenReturn(List.of());
        when(accordRepository.findAccordsTerminesEchange(any())).thenReturn(List.of());

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        assertThat(result.prochainAccords()).isEmpty();
        assertThat(result.accordsEnAttente()).isEmpty();
        assertThat(result.economiesEstimees()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.nbAccordsTermines()).isEqualTo(0);
    }

    // ─── countdown expiration : accord créé il y a 70h → 2h restantes ───────

    @Test
    void shouldCalculateCountdownCorrectly() {
        Accord enAttente = Accord.builder()
                .id(UUID.randomUUID())
                .initiatorId(bob.getId())
                .receiverId(alice.getId())
                .type(AccordType.ECHANGE_TOTAL).statut(AccordStatut.EN_ATTENTE)
                .createdAt(OffsetDateTime.now().minusHours(70))
                .build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findProchainAccords(any(), any())).thenReturn(List.of());
        when(accordRepository.findAccordsEnAttenteForReceiver(alice.getId()))
                .thenReturn(List.of(enAttente));
        when(accordRepository.findAccordsTerminesEchange(any())).thenReturn(List.of());

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        long heures = result.accordsEnAttente().get(0).heuresAvantExpiration();
        // 72 - 70 = 2h restantes (±1h de marge pour l'exécution du test)
        assertThat(heures).isBetween(1L, 3L);
    }
}
