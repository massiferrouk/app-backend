package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.CompatibiliteType;
import com.studup.backend.model.enums.RythmeAlternance;
import com.studup.backend.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PartialExchangeOptimizerTest {

    private PartialExchangeOptimizer optimizer;
    private AlternantProfile profileA;
    private AlternantProfile profileB;

    @BeforeEach
    void setUp() {
        optimizer = new PartialExchangeOptimizer(new CompatibilityCalculator());

        User userA = User.builder().id(UUID.randomUUID()).email("a@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        User userB = User.builder().id(UUID.randomUUID()).email("b@studup.fr")
                .firstName("Bob").lastName("B").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        profileA = AlternantProfile.builder().id(UUID.randomUUID()).user(userA)
                .villeA("Paris").villeB("Lyon").rythme(RythmeAlternance.SEMAINE_3_1).build();

        profileB = AlternantProfile.builder().id(UUID.randomUUID()).user(userB)
                .villeA("Lyon").villeB("Paris").rythme(RythmeAlternance.SEMAINE_3_1).build();
    }

    // Génère 4 semaines : S1=A à Paris/B à Lyon, S2 idem, S3 idem, S4=A à Lyon/B à Paris
    // profileA : villeA=Paris, villeB=Lyon → label B = à Lyon
    // profileB : villeA=Lyon, villeB=Paris → label B = à Paris
    // Semaines 1-3 : A à Lyon, B à Paris → villes inversées → ECHANGE
    // Semaine 4   : A à Paris (label A), B à Lyon (label A) → villes inversées → ECHANGE
    private List<AlternanceSchedule> schedulesA() {
        LocalDate lundi = LocalDate.of(2026, 1, 5);
        return List.of(
                schedule(profileA, lundi, "B"),
                schedule(profileA, lundi.plusWeeks(1), "B"),
                schedule(profileA, lundi.plusWeeks(2), "B"),
                schedule(profileA, lundi.plusWeeks(3), "A")
        );
    }

    private List<AlternanceSchedule> schedulesB() {
        LocalDate lundi = LocalDate.of(2026, 1, 5);
        return List.of(
                schedule(profileB, lundi, "B"),
                schedule(profileB, lundi.plusWeeks(1), "B"),
                schedule(profileB, lundi.plusWeeks(2), "B"),
                schedule(profileB, lundi.plusWeeks(3), "A")
        );
    }

    private AlternanceSchedule schedule(AlternantProfile profile, LocalDate semaine, String label) {
        return AlternanceSchedule.builder()
                .id(UUID.randomUUID()).profile(profile)
                .semaine(semaine).label(label).isOverridden(false).build();
    }

    // ─── shouldMaximizeNonOverlappingWeeks ────────────────────────────────────

    @Test
    void shouldMaximizeNonOverlappingWeeks() {
        PartialExchangeProposal proposal = optimizer.optimize(
                profileA, profileB, schedulesA(), schedulesB(), null);

        // Rythmes inversés → 4 semaines d'échange, 0 chevauchement
        assertThat(proposal.nbSemainesEchange()).isEqualTo(4);
        assertThat(proposal.nbSemainesChevauchement()).isEqualTo(0);
        assertThat(proposal.semainesProposees()).hasSize(4);
        assertThat(proposal.semainesProposees())
                .allMatch(s -> s.type() == CompatibiliteType.ECHANGE);
    }

    // ─── shouldExcludeChevauchementWeeks ──────────────────────────────────────

    @Test
    void shouldExcludeChevauchementWeeks() {
        // Même rythme et mêmes labels pour A et C → même ville chaque semaine → COLOCATION, 0 échange
        AlternantProfile profileC = AlternantProfile.builder().id(UUID.randomUUID())
                .user(profileB.getUser())
                .villeA("Paris").villeB("Lyon").rythme(RythmeAlternance.SEMAINE_3_1).build();

        LocalDate lundi = LocalDate.of(2026, 1, 5);
        // Même labels que schedulesA() → A et C sont dans la même ville chaque semaine → 0 ECHANGE
        List<AlternanceSchedule> schedulesC = List.of(
                schedule(profileC, lundi, "B"),
                schedule(profileC, lundi.plusWeeks(1), "B"),
                schedule(profileC, lundi.plusWeeks(2), "B"),
                schedule(profileC, lundi.plusWeeks(3), "A")
        );

        PartialExchangeProposal proposal = optimizer.optimize(
                profileA, profileC, schedulesA(), schedulesC, null);

        // Même rythme et même ville chaque semaine → 0 échange
        assertThat(proposal.nbSemainesEchange()).isEqualTo(0);
        assertThat(proposal.semainesProposees()).isEmpty();
    }

    // ─── shouldCalculateEconomieWithLoyer ─────────────────────────────────────

    @Test
    void shouldCalculateEconomieWithLoyer() {
        BigDecimal loyer = new BigDecimal("800.00");

        PartialExchangeProposal proposal = optimizer.optimize(
                profileA, profileB, schedulesA(), schedulesB(), loyer);

        // 4 / 4.33 = 0.92 (arrondi) × 800 = 736.00 €
        assertThat(proposal.economieTotale()).isGreaterThan(BigDecimal.ZERO);
        assertThat(proposal.economieTotale()).isEqualByComparingTo(new BigDecimal("736.00"));
    }

    // ─── shouldReturnZeroEconomieWhenNoLoyer ──────────────────────────────────

    @Test
    void shouldReturnZeroEconomieWhenNoLoyer() {
        PartialExchangeProposal proposal = optimizer.optimize(
                profileA, profileB, schedulesA(), schedulesB(), null);

        assertThat(proposal.economieTotale()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── shouldIncludeNbChevauchementInMessage ────────────────────────────────

    @Test
    void shouldIncludeNbChevauchementInMessage() {
        // Un rythme SEMAINE_1_1 crée des chevauchements avec SEMAINE_3_1
        AlternantProfile profileD = AlternantProfile.builder().id(UUID.randomUUID())
                .user(profileB.getUser())
                .villeA("Lyon").villeB("Paris").rythme(RythmeAlternance.SEMAINE_1_1).build();

        LocalDate lundi = LocalDate.of(2026, 1, 5);
        // B alterne chaque semaine : B, A, B, A
        List<AlternanceSchedule> schedulesD = List.of(
                schedule(profileD, lundi, "B"),
                schedule(profileD, lundi.plusWeeks(1), "A"),
                schedule(profileD, lundi.plusWeeks(2), "B"),
                schedule(profileD, lundi.plusWeeks(3), "A")
        );

        PartialExchangeProposal proposal = optimizer.optimize(
                profileA, profileD, schedulesA(), schedulesD, null);

        assertThat(proposal.messageResume()).isNotBlank();
        // Le résumé indique les semaines d'échange
        assertThat(proposal.messageResume()).contains("sem. d'échange proposées");
    }

    // ─── shouldReturnEmptyProposalWhenNoSchedules ─────────────────────────────

    @Test
    void shouldReturnEmptyProposalWhenNoSchedules() {
        PartialExchangeProposal proposal = optimizer.optimize(
                profileA, profileB, List.of(), List.of(), null);

        assertThat(proposal.nbSemainesEchange()).isEqualTo(0);
        assertThat(proposal.semainesProposees()).isEmpty();
        assertThat(proposal.economieTotale()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
