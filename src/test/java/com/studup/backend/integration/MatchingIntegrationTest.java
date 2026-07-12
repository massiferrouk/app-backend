package com.studup.backend.integration;

import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.CompatibiliteType;
import com.studup.backend.model.enums.RythmeAlternance;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flow E2E-02 : compatibilité des rythmes d'alternance sur vraie BDD.
 *
 * Ces tests vérifient que l'algorithme CompatibilityCalculator produit
 * les bons résultats avec de vrais profils persistés en base PostgreSQL.
 *
 * Test de performance : 500 paires calculées en moins de 500ms.
 */
@Transactional
class MatchingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CompatibilityCalculator calculator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AlternantProfileRepository profileRepository;

    // ─── rythmes inverses → ECHANGE_TOTAL avec score = 1.0 ──────────────────

    @Test
    void shouldReturnEchangeTotalForInverseRythms() {
        // Profil A : 3 semaines Paris / 1 semaine Lyon (SEMAINE_3_1)
        AlternantProfile profileA = buildProfile("Paris", "Lyon");
        List<AlternanceSchedule> schedulesA = buildSchedules3_1(profileA, "A", LocalDate.of(2026, 1, 5));

        // Profil B : rythme inverse — villeA=Lyon, label "A" = Lyon pendant que A est à Paris → ÉCHANGE
        AlternantProfile profileB = buildProfile("Lyon", "Paris");
        List<AlternanceSchedule> schedulesB = buildSchedules3_1(profileB, "A", LocalDate.of(2026, 1, 5));

        // MatchingResult est un record Java — on utilise les accesseurs record (sans "get")
        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        assertThat(result.score()).isGreaterThanOrEqualTo(0.90);
        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_TOTAL);
        assertThat(result.nbSemainesEchange()).isGreaterThan(0);
        assertThat(result.semaines()).isNotEmpty();
        assertThat(result.semaines()).allSatisfy(s ->
                assertThat(s.type()).isEqualTo(CompatibiliteType.ECHANGE));
    }

    // ─── même rythme, mêmes villes → COLOCATION_TOURNANTE ───────────────────

    @Test
    void shouldReturnColocationForSameRythm() {
        AlternantProfile profileA = buildProfile("Paris", "Lyon");
        AlternantProfile profileB = buildProfile("Paris", "Lyon");

        List<AlternanceSchedule> schedulesA = buildSchedules3_1(profileA, "A", LocalDate.of(2026, 1, 5));
        List<AlternanceSchedule> schedulesB = buildSchedules3_1(profileB, "A", LocalDate.of(2026, 1, 5));

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        assertThat(result.typePropose()).isEqualTo(AccordType.COLOCATION_TOURNANTE);
        assertThat(result.nbSemainesColocation()).isGreaterThan(0);
        // score = 0 pour une colocation (pas de semaines d'échange)
        assertThat(result.score()).isEqualTo(0.0);
    }

    // ─── schedules vides → résultat vide, pas d'exception ────────────────────

    @Test
    void shouldHandleEmptySchedulesGracefully() {
        AlternantProfile profileA = buildProfile("Paris", "Lyon");
        AlternantProfile profileB = buildProfile("Lyon", "Paris");

        MatchingResult result = calculator.calculate(profileA, profileB, List.of(), List.of());

        assertThat(result.nbSemainesEchange()).isEqualTo(0);
        assertThat(result.nbSemainesColocation()).isEqualTo(0);
        assertThat(result.semaines()).isEmpty();
    }

    // ─── score jamais > 1.0 ──────────────────────────────────────────────────

    @Test
    void shouldNeverExceedScoreOfOne() {
        AlternantProfile profileA = buildProfile("Paris", "Lyon");
        AlternantProfile profileB = buildProfile("Lyon", "Paris");

        List<AlternanceSchedule> schedulesA = buildSchedules3_1(profileA, "A", LocalDate.of(2026, 1, 5));
        // "A" pour B aussi : B est à Lyon (villeA) pendant que A est à Paris → ÉCHANGE sur chaque semaine
        List<AlternanceSchedule> schedulesB = buildSchedules3_1(profileB, "A", LocalDate.of(2026, 1, 5));

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        assertThat(result.score()).isLessThanOrEqualTo(1.0);
        assertThat(result.score()).isGreaterThanOrEqualTo(0.0);
    }

    // ─── performance : 500 paires < 500ms ────────────────────────────────────

    @Test
    void shouldCalculate500PairsUnder500ms() {
        AlternantProfile profileA = buildProfile("Paris", "Lyon");
        AlternantProfile profileB = buildProfile("Lyon", "Paris");

        List<AlternanceSchedule> schedulesA = buildSchedules3_1(profileA, "A", LocalDate.of(2026, 1, 5));
        List<AlternanceSchedule> schedulesB = buildSchedules3_1(profileB, "B", LocalDate.of(2026, 1, 5));

        long start = System.currentTimeMillis();

        for (int i = 0; i < 500; i++) {
            calculator.calculate(profileA, profileB, schedulesA, schedulesB);
        }

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isLessThan(500L);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldPreselectCandidatesIgnoringCityCase() {
        // APP-92 : villes saisies en casses différentes ("Marseille"/"Paris"
        // vs "paris"/"marseille") doivent quand même se présélectionner
        AlternantProfile anna = buildProfile("Marseille", "Paris");
        AlternantProfile bob = buildProfile("paris", "marseille");

        // Bob cherche des candidats — Anna doit sortir malgré la casse différente
        List<AlternantProfile> candidates = profileRepository.findCandidatesWithSharedCity(
                bob.getId(), bob.getVilleA(), bob.getVilleB());

        assertThat(candidates)
                .extracting(AlternantProfile::getId)
                .contains(anna.getId());
    }

    private AlternantProfile buildProfile(String villeA, String villeB) {
        User user = userRepository.save(User.builder()
                .email("user_" + UUID.randomUUID() + "@studup.fr")
                .passwordHash("hash")
                .firstName("Test").lastName("User")
                .role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build());

        // AlternantProfile a un @OneToOne vers User (pas un UUID) — on passe l'entité directement
        return profileRepository.save(AlternantProfile.builder()
                .user(user)
                .villeA(villeA).villeB(villeB)
                .ecole("YNOV").entreprise("TechCorp")
                .dateDebut(LocalDate.of(2026, 1, 1))
                .dateFin(LocalDate.of(2026, 12, 31))
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build());
    }

    /**
     * Génère 52 semaines labellisées selon le rythme 3 semaines / 1 semaine.
     * startLabel = "A" ou "B" détermine si on commence côté école ou entreprise.
     */
    private List<AlternanceSchedule> buildSchedules3_1(
            AlternantProfile profile, String startLabel, LocalDate startDate) {

        List<AlternanceSchedule> schedules = new ArrayList<>();
        LocalDate current = startDate;

        for (int i = 0; i < 52; i++) {
            // Cycle 3-1 : AAA B AAA B...
            String label = (i % 4 == 3) ? (startLabel.equals("A") ? "B" : "A") : startLabel;

            schedules.add(AlternanceSchedule.builder()
                    .profile(profile)
                    .semaine(current)
                    .label(label)
                    .isOverridden(false)
                    .createdAt(OffsetDateTime.now())
                    .build());

            current = current.plusWeeks(1);
        }

        return schedules;
    }
}
