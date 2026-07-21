package com.studup.backend.service;

import com.studup.backend.model.dto.response.AlternantDashboardResponse;
import com.studup.backend.model.dto.response.MatchingSuggestionResponse;
import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AccordRepository;
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
    @Mock private MatchingService matchingService;

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

    /** Suggestion minimale portant une économie possible */
    private MatchingSuggestionResponse suggestion(BigDecimal economie) {
        return new MatchingSuggestionResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Bob", "B",
                "Paris", "Lyon", 0.75, 75,
                AccordType.ECHANGE_PARTIEL, true, null,
                3, 0, 1, null, List.of(),
                null, null, economie, List.of());
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
        when(matchingService.getSuggestions("alice@studup.fr")).thenReturn(List.of());

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        assertThat(result.prochainAccords()).hasSize(1);
        assertThat(result.accordsEnAttente()).hasSize(1);
        assertThat(result.accordsEnAttente().get(0).heuresAvantExpiration()).isNotNull();
        assertThat(result.accordsEnAttente().get(0).heuresAvantExpiration()).isLessThanOrEqualTo(72L);
    }

    // ─── KPIs vivants : nb de matches + meilleure économie POSSIBLE (APP-120) ─

    @Test
    void shouldExposeMatchCountAndBestPossibleSaving() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findProchainAccords(any(), any())).thenReturn(List.of());
        when(accordRepository.findAccordsEnAttenteForReceiver(any())).thenReturn(List.of());
        when(matchingService.getSuggestions("alice@studup.fr")).thenReturn(List.of(
                suggestion(BigDecimal.valueOf(150)),
                suggestion(BigDecimal.valueOf(283)),
                suggestion(BigDecimal.valueOf(90))));

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        assertThat(result.nbMatchesCompatibles()).isEqualTo(3);
        // On met en avant le MEILLEUR potentiel, pas la somme
        assertThat(result.economiePossibleMax()).isEqualByComparingTo(BigDecimal.valueOf(283));
    }

    // ─── aucune économie chiffrée → 0, jamais de chiffre inventé ─────────────

    @Test
    void shouldReturnZeroSavingWhenNoRentKnown() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findProchainAccords(any(), any())).thenReturn(List.of());
        when(accordRepository.findAccordsEnAttenteForReceiver(any())).thenReturn(List.of());
        when(matchingService.getSuggestions("alice@studup.fr"))
                .thenReturn(List.of(suggestion(BigDecimal.ZERO)));

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        assertThat(result.nbMatchesCompatibles()).isEqualTo(1);
        assertThat(result.economiePossibleMax()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── dashboard vide ───────────────────────────────────────────────────────

    @Test
    void shouldReturnEmptyDashboard() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(accordRepository.findProchainAccords(any(), any())).thenReturn(List.of());
        when(accordRepository.findAccordsEnAttenteForReceiver(any())).thenReturn(List.of());
        when(matchingService.getSuggestions(any())).thenReturn(List.of());

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        assertThat(result.prochainAccords()).isEmpty();
        assertThat(result.accordsEnAttente()).isEmpty();
        assertThat(result.economiePossibleMax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.nbMatchesCompatibles()).isEqualTo(0);
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
        when(matchingService.getSuggestions(any())).thenReturn(List.of());

        AlternantDashboardResponse result = service.getDashboard("alice@studup.fr");

        long heures = result.accordsEnAttente().get(0).heuresAvantExpiration();
        // 72 - 70 = 2h restantes (±1h de marge pour l'exécution du test)
        assertThat(heures).isBetween(1L, 3L);
    }
}
