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

class ColocationMatcherTest {

    private ColocationMatcher matcher;
    // Profil A et C : même rythme, mêmes villes → colocation
    private AlternantProfile profileA;
    private AlternantProfile profileC;
    // Profil B : villes inversées par rapport à A → échange (pas colocation)
    private AlternantProfile profileB;

    private static final LocalDate LUNDI = LocalDate.of(2026, 1, 5);

    @BeforeEach
    void setUp() {
        matcher = new ColocationMatcher(new CompatibilityCalculator());

        User userA = User.builder().id(UUID.randomUUID()).email("a@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        User userB = User.builder().id(UUID.randomUUID()).email("b@studup.fr")
                .firstName("Bob").lastName("B").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        User userC = User.builder().id(UUID.randomUUID()).email("c@studup.fr")
                .firstName("Carol").lastName("C").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        profileA = AlternantProfile.builder().id(UUID.randomUUID()).user(userA)
                .villeA("Paris").villeB("Lyon").rythme(RythmeAlternance.SEMAINE_3_1).build();

        // B : villes inversées → échange avec A, pas colocation
        profileB = AlternantProfile.builder().id(UUID.randomUUID()).user(userB)
                .villeA("Lyon").villeB("Paris").rythme(RythmeAlternance.SEMAINE_3_1).build();

        // C : mêmes villes que A → colocation
        profileC = AlternantProfile.builder().id(UUID.randomUUID()).user(userC)
                .villeA("Paris").villeB("Lyon").rythme(RythmeAlternance.SEMAINE_3_1).build();
    }

    private AlternanceSchedule schedule(AlternantProfile profile, LocalDate semaine, String label) {
        return AlternanceSchedule.builder()
                .id(UUID.randomUUID()).profile(profile)
                .semaine(semaine).label(label).isOverridden(false).build();
    }

    // Même rythme + même labels → même ville chaque semaine → COLOCATION
    private List<AlternanceSchedule> schedulesA() {
        return List.of(
                schedule(profileA, LUNDI, "B"),
                schedule(profileA, LUNDI.plusWeeks(1), "B"),
                schedule(profileA, LUNDI.plusWeeks(2), "B"),
                schedule(profileA, LUNDI.plusWeeks(3), "A")
        );
    }

    private List<AlternanceSchedule> schedulesC() {
        return List.of(
                schedule(profileC, LUNDI, "B"),
                schedule(profileC, LUNDI.plusWeeks(1), "B"),
                schedule(profileC, LUNDI.plusWeeks(2), "B"),
                schedule(profileC, LUNDI.plusWeeks(3), "A")
        );
    }

    // ─── shouldFindUsersWithSameRhythmAndCities ───────────────────────────────

    @Test
    void shouldFindUsersWithSameRhythmAndCities() {
        ColocationProposal proposal = matcher.match(
                profileA, profileC, schedulesA(), schedulesC(), null, null);

        // Même rythme + mêmes villes → 4 semaines de colocation
        assertThat(proposal.nbSemainesColocation()).isEqualTo(4);
        assertThat(proposal.semainesColocation()).hasSize(4);
        assertThat(proposal.semainesColocation())
                .allMatch(s -> s.type() == CompatibiliteType.COLOCATION);
    }

    // ─── shouldReturnZeroColocationForInvertedRhythms ─────────────────────────

    @Test
    void shouldReturnZeroColocationForInvertedRhythms() {
        // A (Paris/Lyon) et B (Lyon/Paris) avec labels miroirs → ECHANGE, 0 colocation
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profileB, LUNDI, "B"),
                schedule(profileB, LUNDI.plusWeeks(1), "B"),
                schedule(profileB, LUNDI.plusWeeks(2), "B"),
                schedule(profileB, LUNDI.plusWeeks(3), "A")
        );

        ColocationProposal proposal = matcher.match(
                profileA, profileB, schedulesA(), schedulesB, null, null);

        assertThat(proposal.nbSemainesColocation()).isEqualTo(0);
        assertThat(proposal.semainesColocation()).isEmpty();
    }

    // ─── shouldCalculateEconomieMensuelle ─────────────────────────────────────

    @Test
    void shouldCalculateEconomieMensuelle() {
        BigDecimal loyerA = new BigDecimal("800.00");
        BigDecimal loyerB = new BigDecimal("600.00");

        ColocationProposal proposal = matcher.match(
                profileA, profileC, schedulesA(), schedulesC(), loyerA, loyerB);

        // (800 + 600) / 2 = 700 €/mois
        assertThat(proposal.economieMensuelle()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    // ─── shouldCalculateEconomieTotale ────────────────────────────────────────

    @Test
    void shouldCalculateEconomieTotale() {
        BigDecimal loyerA = new BigDecimal("800.00");
        BigDecimal loyerB = new BigDecimal("600.00");

        ColocationProposal proposal = matcher.match(
                profileA, profileC, schedulesA(), schedulesC(), loyerA, loyerB);

        // économie totale = 700 × (4 / 4.33) = 700 × 0.92 = 644.00
        assertThat(proposal.economieTotaleEstimee()).isGreaterThan(BigDecimal.ZERO);
        assertThat(proposal.economieTotaleEstimee()).isEqualByComparingTo(new BigDecimal("644.00"));
    }

    // ─── shouldReturnZeroEconomieWhenNoLoyer ──────────────────────────────────

    @Test
    void shouldReturnZeroEconomieWhenNoLoyer() {
        ColocationProposal proposal = matcher.match(
                profileA, profileC, schedulesA(), schedulesC(), null, null);

        assertThat(proposal.economieMensuelle()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(proposal.economieTotaleEstimee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── shouldDetectVillePrincipale ──────────────────────────────────────────

    @Test
    void shouldDetectVillePrincipale() {
        ColocationProposal proposal = matcher.match(
                profileA, profileC, schedulesA(), schedulesC(), null, null);

        // 3 semaines à Lyon (label B = villeB de profileA) + 1 semaine à Paris
        // La ville principale doit être Lyon
        assertThat(proposal.villeColocationPrincipale()).isEqualTo("Lyon");
    }

    // ─── shouldReturnEmptyProposalWhenNoSchedules ─────────────────────────────

    @Test
    void shouldReturnEmptyProposalWhenNoSchedules() {
        ColocationProposal proposal = matcher.match(
                profileA, profileC, List.of(), List.of(), null, null);

        assertThat(proposal.nbSemainesColocation()).isEqualTo(0);
        assertThat(proposal.semainesColocation()).isEmpty();
        assertThat(proposal.villeColocationPrincipale()).isNull();
    }
}
