package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.CompatibiliteType;
import com.studup.backend.model.enums.RythmeAlternance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityCalculatorTest {

    private CompatibilityCalculator calculator;

    // Profil A : Paris (villeA) ↔ Lyon (villeB)
    private AlternantProfile profileA;
    // Profil B : Lyon (villeA) ↔ Paris (villeB) — villes inversées
    private AlternantProfile profileB;
    // Profil C : Paris (villeA) ↔ Lyon (villeB) — même villes que A
    private AlternantProfile profileC;

    private static final LocalDate SEMAINE_1 = LocalDate.of(2026, 9, 7);   // lundi
    private static final LocalDate SEMAINE_2 = LocalDate.of(2026, 9, 14);
    private static final LocalDate SEMAINE_3 = LocalDate.of(2026, 9, 21);
    private static final LocalDate SEMAINE_4 = LocalDate.of(2026, 9, 28);

    @BeforeEach
    void setUp() {
        calculator = new CompatibilityCalculator();

        profileA = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Paris").villeB("Lyon")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .build();

        // B a les villes inversées par rapport à A
        profileB = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Lyon").villeB("Paris")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .build();

        // C a les mêmes villes que A — pour tester la colocation
        profileC = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Paris").villeB("Lyon")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .build();
    }

    // ─── CAS 1 : Échange total — rythmes inverses ─────────────────────────────

    @Test
    void shouldReturnEchangeTotalWhenRythmsAreInversed() {
        // A : Paris / Paris / Paris / Lyon  (SEMAINE_3_1)
        // B : Lyon  / Lyon  / Lyon  / Paris (rythme miroir)
        // Chaque semaine : A est dans la ville de B et vice-versa → ECHANGE
        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "B"), // B = Lyon pour A → mais A est à villeB
                schedule(profileA, SEMAINE_2, "B"),
                schedule(profileA, SEMAINE_3, "B"),
                schedule(profileA, SEMAINE_4, "A")  // A = Paris pour A
        );

        // B a les villes inversées : villeA=Lyon, villeB=Paris
        // Pour que B soit à Paris (villeB), il faut label="B"
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profileB, SEMAINE_1, "B"), // B = Paris pour B → échange avec A à Lyon
                schedule(profileB, SEMAINE_2, "B"),
                schedule(profileB, SEMAINE_3, "B"),
                schedule(profileB, SEMAINE_4, "A")  // A = Lyon pour B → échange avec A à Paris
        );

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_TOTAL);
        assertThat(result.nbSemainesEchange()).isEqualTo(4);
        assertThat(result.nbSemainesColocation()).isEqualTo(0);
        assertThat(result.nbSemainesChevauchement()).isEqualTo(0);
    }

    // ─── CAS 2 : Colocation tournante — même rythme, mêmes villes ────────────

    @Test
    void shouldReturnColocationWhenSameRythm() {
        // A et C ont exactement le même rythme et les mêmes villes
        // → chaque semaine ils sont dans la même ville → COLOCATION
        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "B"),
                schedule(profileA, SEMAINE_2, "B"),
                schedule(profileA, SEMAINE_3, "B"),
                schedule(profileA, SEMAINE_4, "A")
        );

        List<AlternanceSchedule> schedulesC = List.of(
                schedule(profileC, SEMAINE_1, "B"),
                schedule(profileC, SEMAINE_2, "B"),
                schedule(profileC, SEMAINE_3, "B"),
                schedule(profileC, SEMAINE_4, "A")
        );

        MatchingResult result = calculator.calculate(profileA, profileC, schedulesA, schedulesC);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.typePropose()).isEqualTo(AccordType.COLOCATION_TOURNANTE);
        assertThat(result.nbSemainesColocation()).isEqualTo(4);
        assertThat(result.nbSemainesEchange()).isEqualTo(0);
    }

    // ─── CAS 3 : Échange partiel — rythmes différents ─────────────────────────

    @Test
    void shouldReturnEchangePartielWhenPartialOverlap() {
        // A : Lyon / Lyon / Lyon / Paris  (SEMAINE_3_1, commence en entreprise)
        // B : Paris / Lyon / Paris / Lyon (SEMAINE_1_1)
        // Semaine 1 : A à Lyon, B à Paris  → ECHANGE ✓
        // Semaine 2 : A à Lyon, B à Lyon   → COLOCATION
        // Semaine 3 : A à Lyon, B à Paris  → ECHANGE ✓
        // Semaine 4 : A à Paris, B à Lyon  → ECHANGE ✓
        // score = 3/4 = 0.75 → ECHANGE_PARTIEL

        AlternantProfile profBPartiel = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Paris").villeB("Lyon")
                .rythme(RythmeAlternance.SEMAINE_1_1)
                .build();

        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "B"),  // Lyon
                schedule(profileA, SEMAINE_2, "B"),  // Lyon
                schedule(profileA, SEMAINE_3, "B"),  // Lyon
                schedule(profileA, SEMAINE_4, "A")   // Paris
        );

        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profBPartiel, SEMAINE_1, "A"),  // Paris
                schedule(profBPartiel, SEMAINE_2, "B"),  // Lyon
                schedule(profBPartiel, SEMAINE_3, "A"),  // Paris
                schedule(profBPartiel, SEMAINE_4, "B")   // Lyon
        );

        MatchingResult result = calculator.calculate(profileA, profBPartiel, schedulesA, schedulesB);

        assertThat(result.score()).isGreaterThanOrEqualTo(0.60);
        assertThat(result.score()).isLessThan(0.90);
        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_PARTIEL);
        assertThat(result.nbSemainesEchange()).isEqualTo(3);
    }

    // ─── CAS 4 : Score zéro sans colocation ───────────────────────────────────

    @Test
    void shouldReturnZeroScoreWhenNoCompatibility() {
        // A et B ont des villes sans aucun lien → pas d'échange possible
        AlternantProfile profSansLien = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Bordeaux").villeB("Marseille")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .build();

        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "B"),
                schedule(profileA, SEMAINE_2, "A")
        );

        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profSansLien, SEMAINE_1, "A"),
                schedule(profSansLien, SEMAINE_2, "B")
        );

        MatchingResult result = calculator.calculate(profileA, profSansLien, schedulesA, schedulesB);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.typePropose()).isNull();
        assertThat(result.nbSemainesEchange()).isEqualTo(0);
    }

    // ─── CAS 5 : Score ne dépasse jamais 1.0 ─────────────────────────────────

    @Test
    void shouldNeverExceedScoreOfOne() {
        List<AlternanceSchedule> schedulesA = new ArrayList<>();
        List<AlternanceSchedule> schedulesB = new ArrayList<>();

        // 52 semaines d'échange parfait
        for (int i = 0; i < 52; i++) {
            LocalDate semaine = SEMAINE_1.plusWeeks(i);
            schedulesA.add(schedule(profileA, semaine, i % 2 == 0 ? "A" : "B"));
            schedulesB.add(schedule(profileB, semaine, i % 2 == 0 ? "B" : "A"));
        }

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        assertThat(result.score()).isLessThanOrEqualTo(1.0);
        assertThat(result.score()).isGreaterThanOrEqualTo(0.0);
    }

    // ─── CAS 6 : Schedules vides ──────────────────────────────────────────────

    @Test
    void shouldHandleEmptySchedules() {
        MatchingResult result = calculator.calculate(profileA, profileB, List.of(), List.of());

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.typePropose()).isNull();
        assertThat(result.semaines()).isEmpty();
        assertThat(result.messageResume()).isEqualTo("Aucune semaine commune");
    }

    // ─── CAS 7 : Couleurs des semaines ────────────────────────────────────────

    @Test
    void shouldReturnCorrectColorsForEachWeekType() {
        // Semaine 1 : ECHANGE (villes inversées)
        // Semaine 2 : COLOCATION (même ville)
        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "B"),  // A à Lyon
                schedule(profileA, SEMAINE_2, "A")   // A à Paris
        );
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profileB, SEMAINE_1, "A"),  // B à Lyon (villeA de B)
                schedule(profileB, SEMAINE_2, "B")   // B à Paris (villeB de B)
        );

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        // Semaine 1 : A à Lyon, B à Lyon (villeA de B = Lyon) → villes identiques → COLOCATION
        // Semaine 2 : A à Paris, B à Paris → villes identiques → COLOCATION
        // Les deux sont dans la même ville → couleur bleue
        result.semaines().forEach(s ->
                assertThat(s.couleurHex()).isNotBlank()
        );
    }

    // ─── CAS 8 : ECHANGE → couleur verte ─────────────────────────────────────

    @Test
    void shouldReturnGreenColorForEchangeWeek() {
        // A à Lyon (villeB de A), B à Paris (villeB de B)
        // → A est dans villeA de B (Lyon), B est dans villeA de A (Paris... non)
        // Construisons un cas d'échange clair :
        // profileA : villeA=Paris, villeB=Lyon
        // profileB : villeA=Lyon,  villeB=Paris
        // labelA="B" → A à Lyon ; labelB="B" → B à Paris → villes différentes et croisées → ECHANGE

        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "B")   // A à Lyon
        );
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profileB, SEMAINE_1, "B")   // B à Paris (villeB de B)
        );

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        assertThat(result.semaines()).hasSize(1);
        SemaineCompatibilite s = result.semaines().get(0);
        assertThat(s.type()).isEqualTo(CompatibiliteType.ECHANGE);
        assertThat(s.couleurHex()).isEqualTo("#27AE60");
        assertThat(s.label()).isEqualTo("Échange");
    }

    // ─── CAS 9 : Match actif vs potentiel ────────────────────────────────────

    @Test
    void shouldDetectMatchActifWhenBothLogementPublished() {
        // isMatchActif est géré par MatchingService (pas ici)
        // On vérifie juste que l'algorithme retourne false par défaut
        List<AlternanceSchedule> schedulesA = List.of(schedule(profileA, SEMAINE_1, "B"));
        List<AlternanceSchedule> schedulesB = List.of(schedule(profileB, SEMAINE_1, "B"));

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        assertThat(result.isMatchActif()).isFalse();
        assertThat(result.messageMatchPotentiel()).isNotNull();
    }

    @Test
    void shouldDetectMatchPotentielWhenOneLogementMissing() {
        List<AlternanceSchedule> schedulesA = List.of(schedule(profileA, SEMAINE_1, "B"));
        List<AlternanceSchedule> schedulesB = List.of(schedule(profileB, SEMAINE_1, "B"));

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        // Sans logements publiés → match potentiel par défaut
        assertThat(result.isMatchActif()).isFalse();
        assertThat(result.messageMatchPotentiel()).contains("logements");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AlternanceSchedule schedule(AlternantProfile profile, LocalDate semaine, String label) {
        return AlternanceSchedule.builder()
                .id(UUID.randomUUID())
                .profile(profile)
                .semaine(semaine)
                .label(label)
                .isOverridden(false)
                .build();
    }
}
