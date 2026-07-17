package com.studup.backend.service;

import com.studup.backend.algorithm.ColocationMatcher;
import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.algorithm.PartialExchangeOptimizer;
import com.studup.backend.algorithm.Scenario;
import com.studup.backend.algorithm.ScenarioAdvisor;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MatchingSuggestionResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.RythmeAlternance;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock private AlternantProfileRepository profileRepository;
    @Mock private AlternanceScheduleRepository scheduleRepository;
    @Mock private UserRepository userRepository;
    @Mock private LogementRepository logementRepository;
    @Mock private CompatibilityCalculator calculator;
    @Mock private PartialExchangeOptimizer partialExchangeOptimizer;
    @Mock private ColocationMatcher colocationMatcher;
    // @Spy : le vrai moteur de scénarios — classe pure sans dépendance,
    // le mocker n'apporterait rien (même choix que ScheduleGenerator)
    @org.mockito.Spy private ScenarioAdvisor scenarioAdvisor = new ScenarioAdvisor();

    @InjectMocks
    private MatchingService matchingService;

    private User myUser;
    private AlternantProfile myProfile;
    private AlternantProfile candidatProfile;

    @BeforeEach
    void setUp() {
        myUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@studup.fr")
                .firstName("Alice").lastName("Martin")
                .role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        myProfile = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .user(myUser)
                .villeA("Paris").villeB("Lyon")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .build();

        User candidatUser = User.builder()
                .id(UUID.randomUUID())
                .email("bob@studup.fr")
                .firstName("Bob").lastName("Dupont")
                .role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        candidatProfile = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .user(candidatUser)
                .villeA("Lyon").villeB("Paris")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .build();
    }

    // ─── Suggestions triées par score décroissant ─────────────────────────────

    @Test
    void shouldReturnSortedSuggestions() {
        User carolUser = User.builder()
                .id(UUID.randomUUID()).email("carol@studup.fr")
                .firstName("Carol").lastName("Petit")
                .role(UserRole.ALTERNANT).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        AlternantProfile candidat2 = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .user(carolUser)
                .villeA("Paris").villeB("Lyon")
                .rythme(RythmeAlternance.SEMAINE_1_1)
                .build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(myUser));
        when(profileRepository.findByUserId(myUser.getId())).thenReturn(Optional.of(myProfile));
        when(profileRepository.findCandidatesWithSharedCity(
                eq(myProfile.getId()), eq("Paris"), eq("Lyon")))
                .thenReturn(List.of(candidatProfile, candidat2));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(myProfile.getId()))
                .thenReturn(List.of());
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(candidatProfile.getId()))
                .thenReturn(List.of());
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(candidat2.getId()))
                .thenReturn(List.of());

        // candidatProfile : score 1.0 (échange total)
        MatchingResult resultHaut = new MatchingResult(1.0, AccordType.ECHANGE_TOTAL,
                false, "message", List.of(), 4, 4, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, "résumé");

        // candidat2 : score 0.75 (échange partiel)
        MatchingResult resultBas = new MatchingResult(0.75, AccordType.ECHANGE_PARTIEL,
                false, "message", List.of(), 3, 3, 0, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, "résumé");

        when(calculator.calculate(eq(myProfile), eq(candidatProfile), any(), any(), any(), any()))
                .thenReturn(resultHaut);
        when(calculator.calculate(eq(myProfile), eq(candidat2), any(), any(), any(), any()))
                .thenReturn(resultBas);

        List<MatchingSuggestionResponse> suggestions = matchingService.getSuggestions("alice@studup.fr");

        assertThat(suggestions).hasSize(2);
        // Le score le plus élevé doit être en premier
        assertThat(suggestions.get(0).score()).isEqualTo(1.0);
        assertThat(suggestions.get(1).score()).isEqualTo(0.75);
    }

    // ─── Surplus même ville : pas actif + scénarios exposés (APP-109) ─────────

    @Test
    void shouldNotBeActiveAndExposeScenariosWhenBothLogementsInSameCity() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(myUser));
        when(profileRepository.findByUserId(myUser.getId())).thenReturn(Optional.of(myProfile));
        when(profileRepository.findCandidatesWithSharedCity(any(), any(), any()))
                .thenReturn(List.of(candidatProfile));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());

        // Les deux alternants ont publié un logement... dans la MÊME ville
        com.studup.backend.model.entity.Logement monLogement =
                com.studup.backend.model.entity.Logement.builder()
                        .id(UUID.randomUUID()).owner(myUser)
                        .ville("Paris").loyer(new BigDecimal("700"))
                        .statut(com.studup.backend.model.enums.LogementStatut.ACTIF)
                        .villeAssociee(com.studup.backend.model.enums.VilleAssociee.VILLE_A)
                        .build();
        com.studup.backend.model.entity.Logement sonLogement =
                com.studup.backend.model.entity.Logement.builder()
                        .id(UUID.randomUUID()).owner(candidatProfile.getUser())
                        .ville("paris").loyer(new BigDecimal("550"))
                        .statut(com.studup.backend.model.enums.LogementStatut.ACTIF)
                        .villeAssociee(com.studup.backend.model.enums.VilleAssociee.VILLE_A)
                        .build();
        when(logementRepository.findByOwnerId(myUser.getId()))
                .thenReturn(List.of(monLogement));
        when(logementRepository.findByOwnerId(candidatProfile.getUser().getId()))
                .thenReturn(List.of(sonLogement));

        MatchingResult echangeTotal = new MatchingResult(1.0, AccordType.ECHANGE_TOTAL,
                false, null, List.of(), 4, 4, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, "résumé");
        when(calculator.calculate(any(), any(), any(), any(), any(), any()))
                .thenReturn(echangeTotal);

        List<MatchingSuggestionResponse> suggestions =
                matchingService.getSuggestions("alice@studup.fr");

        assertThat(suggestions).hasSize(1);
        // Bug corrigé : deux logements même ville ≠ échange signable
        assertThat(suggestions.get(0).isMatchActif()).isFalse();
        // Le moteur de scénarios propose les options (relais en tête)
        assertThat(suggestions.get(0).scenarios()).isNotEmpty();
        assertThat(suggestions.get(0).scenarios().get(0).type())
                .isEqualTo(Scenario.ScenarioType.RELAIS.name());
    }

    // ─── Inclusion des matchs potentiels ──────────────────────────────────────

    @Test
    void shouldIncludePotentialMatches() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(myUser));
        when(profileRepository.findByUserId(myUser.getId())).thenReturn(Optional.of(myProfile));
        when(profileRepository.findCandidatesWithSharedCity(any(), any(), any()))
                .thenReturn(List.of(candidatProfile));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());

        // Match potentiel : isMatchActif=false, messageMatchPotentiel non null
        MatchingResult matchPotentiel = new MatchingResult(0.8, AccordType.ECHANGE_PARTIEL,
                false, "Publiez vos logements pour activer ce match",
                List.of(), 3, 3, 0, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, "résumé");

        when(calculator.calculate(any(), any(), any(), any(), any(), any())).thenReturn(matchPotentiel);

        List<MatchingSuggestionResponse> suggestions = matchingService.getSuggestions("alice@studup.fr");

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).isMatchActif()).isFalse();
        assertThat(suggestions.get(0).messageMatchPotentiel()).contains("logements");
    }

    // ─── Filtrage des profils sans compatibilité ──────────────────────────────

    @Test
    void shouldExcludeProfilesWithNoCompatibility() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(myUser));
        when(profileRepository.findByUserId(myUser.getId())).thenReturn(Optional.of(myProfile));
        when(profileRepository.findCandidatesWithSharedCity(any(), any(), any()))
                .thenReturn(List.of(candidatProfile));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());

        // typePropose null = aucune compatibilité → doit être exclu
        MatchingResult sansCompatibilite = new MatchingResult(0.0, null,
                false, null, List.of(), 0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, "Aucune semaine commune");

        when(calculator.calculate(any(), any(), any(), any(), any(), any())).thenReturn(sansCompatibilite);

        List<MatchingSuggestionResponse> suggestions = matchingService.getSuggestions("alice@studup.fr");

        assertThat(suggestions).isEmpty();
    }

    // ─── Erreur si profil alternant absent ────────────────────────────────────

    @Test
    void shouldThrowWhenAlternantProfileNotFound() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(myUser));
        when(profileRepository.findByUserId(myUser.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.getSuggestions("alice@studup.fr"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profil alternant introuvable");
    }

    // ─── scorePercent calculé correctement ───────────────────────────────────

    @Test
    void shouldCalculateScorePercentCorrectly() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(myUser));
        when(profileRepository.findByUserId(myUser.getId())).thenReturn(Optional.of(myProfile));
        when(profileRepository.findCandidatesWithSharedCity(any(), any(), any()))
                .thenReturn(List.of(candidatProfile));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());

        MatchingResult result = new MatchingResult(0.75, AccordType.ECHANGE_PARTIEL,
                false, "message", List.of(), 3, 3, 0, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, "résumé");

        when(calculator.calculate(any(), any(), any(), any(), any(), any())).thenReturn(result);

        List<MatchingSuggestionResponse> suggestions = matchingService.getSuggestions("alice@studup.fr");

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).scorePercent()).isEqualTo(75);
    }
}
