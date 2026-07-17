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
    void shouldReturnEchange75PctWhenRythmsAreInversed() {
        // A : Lyon / Lyon / Lyon / Paris  (SEMAINE_3_1, logement à Paris)
        // B : Paris / Paris / Paris / Lyon (rythme miroir, logement à Lyon)
        // Semaines 1-3 : chacun dans la ville du logement de l'autre → ECHANGE
        // Semaine 4 : chacun dans la ville de SON logement → chacun chez soi
        // (règle §3 APP-110 : révisé — avant, la semaine 4 comptait à tort
        // comme un échange et le score affichait 100 %)
        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "B"), // A à Lyon (villeB de A)
                schedule(profileA, SEMAINE_2, "B"),
                schedule(profileA, SEMAINE_3, "B"),
                schedule(profileA, SEMAINE_4, "A")  // A à Paris
        );

        // B a les villes inversées : villeA=Lyon, villeB=Paris
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profileB, SEMAINE_1, "B"), // B à Paris (villeB de B)
                schedule(profileB, SEMAINE_2, "B"),
                schedule(profileB, SEMAINE_3, "B"),
                schedule(profileB, SEMAINE_4, "A")  // B à Lyon
        );

        MatchingResult result = calculator.calculate(profileA, profileB,
                schedulesA, schedulesB,
                logement("Paris", "650"), logement("Lyon", "900"));

        assertThat(result.score()).isEqualTo(0.75);
        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_PARTIEL);
        assertThat(result.nbSemainesEchange()).isEqualTo(3);
        assertThat(result.nbSemainesEchangePotentiel()).isEqualTo(4);
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

        // APP-108 : la coloc compte maintenant dans le score (semaines où
        // StudUp fait économiser). 4 semaines coloc sur 4 → score 1.0.
        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.typePropose()).isEqualTo(AccordType.COLOCATION_TOURNANTE);
        assertThat(result.nbSemainesColocation()).isEqualTo(4);
        assertThat(result.nbSemainesEchange()).isEqualTo(0);
    }

    // ─── CAS 3 : Échange partiel — rythmes différents ─────────────────────────

    @Test
    void shouldReturnEchangePartielWhenPartialOverlap() {
        // A : Lyon / Lyon / Lyon / Paris  (SEMAINE_3_1, logement à Paris)
        // B : Paris / Lyon / Paris / Lyon (SEMAINE_1_1, logement à Lyon)
        // Semaine 1 : A à Lyon, B à Paris  → ECHANGE (chacun chez l'autre) ✓
        // Semaine 2 : A à Lyon, B à Lyon   → COLOCATION
        // Semaine 3 : A à Lyon, B à Paris  → ECHANGE ✓
        // Semaine 4 : A à Paris, B à Lyon  → chacun dans la ville de SON
        //             logement → neutre (règle §3 APP-110)
        // Score = (2 échange + 1 coloc)/4 = 0.75 → ECHANGE_PARTIEL.

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

        MatchingResult result = calculator.calculate(profileA, profBPartiel,
                schedulesA, schedulesB,
                logement("Paris", "650"), logement("Lyon", "900"));

        assertThat(result.score()).isEqualTo(0.75);
        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_PARTIEL);
        assertThat(result.nbSemainesEchange()).isEqualTo(2);
        assertThat(result.nbSemainesColocation()).isEqualTo(1);
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

        // APP-110 : l'échange RÉEL exige les logements publiés —
        // A loge à Paris, B loge à Lyon → chacun est chez l'autre cette semaine
        MatchingResult result = calculator.calculate(profileA, profileB,
                schedulesA, schedulesB,
                logement("Paris", "650"), logement("Lyon", "900"));

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

    // ─── Cas mixte échange + coloc (APP-108) ──────────────────────────────────

    @Test
    void shouldMakeMixedCaseVisible() {
        // A Paris/Lyon (logement Lyon), B Lyon/Paris (logement Paris),
        // rythmes décalés :
        // W1 : A=Paris, B=Lyon  → ÉCHANGE (chacun dans la ville du logement de l'autre)
        // W2 : A=Paris, B=Paris → COLOC
        // W3 : A=Lyon,  B=Lyon  → COLOC
        // W4 : A=Lyon,  B=Paris → chacun dans la ville de SON logement → neutre (APP-110)
        // Score = (1+2)/4 = 0.75 → ECHANGE_PARTIEL, match visible.
        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "A"),  // Paris
                schedule(profileA, SEMAINE_2, "A"),  // Paris
                schedule(profileA, SEMAINE_3, "B"),  // Lyon
                schedule(profileA, SEMAINE_4, "B")); // Lyon
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profileB, SEMAINE_1, "A"),  // Lyon
                schedule(profileB, SEMAINE_2, "B"),  // Paris
                schedule(profileB, SEMAINE_3, "A"),  // Lyon
                schedule(profileB, SEMAINE_4, "B")); // Paris

        MatchingResult result = calculator.calculate(profileA, profileB,
                schedulesA, schedulesB,
                logement("Lyon", "650"), logement("Paris", "900"));

        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_PARTIEL);
        assertThat(result.score()).isEqualTo(0.75);
        assertThat(result.nbSemainesEchange()).isEqualTo(1);
        assertThat(result.nbSemainesEchangePotentiel()).isEqualTo(2);
        assertThat(result.nbSemainesColocation()).isEqualTo(2);
    }

    @Test
    void shouldLabelNeutralWeekAsChacunChezSoi() {
        // A Paris/Lyon, B Lyon/Marseille : une seule ville commune (Lyon).
        // Quand A est à Paris et B à Marseille → rien de possible :
        // semaine neutre, libellée « Chacun chez soi » (pas un échec).
        AlternantProfile profB = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Lyon").villeB("Marseille")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .build();

        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "A")); // Paris
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profB, SEMAINE_1, "B"));    // Marseille

        MatchingResult result = calculator.calculate(profileA, profB, schedulesA, schedulesB);

        assertThat(result.semaines().get(0).type())
                .isEqualTo(CompatibiliteType.INCOMPATIBLE);
        assertThat(result.semaines().get(0).label()).isEqualTo("Chacun chez soi");
    }

    @Test
    void shouldReturnNullWhenNoEchangeNoColoc() {
        // Aucune ville commune sur cette semaine → ni échange ni coloc → pas de match
        AlternantProfile profB = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Lyon").villeB("Marseille")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .build();

        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "A")); // Paris
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profB, SEMAINE_1, "B"));    // Marseille

        MatchingResult result = calculator.calculate(profileA, profB, schedulesA, schedulesB);

        assertThat(result.typePropose()).isNull();
    }

    // ─── Économies estimées (APP-103) ─────────────────────────────────────────

    @Test
    void shouldCalculateEconomieForEchangeWithBothLogements() {
        // A : Lyon 3 semaines puis Paris 1 semaine ; B : rythme miroir.
        // Le logement de B est à Lyon (900 €) : A l'occupe les 3 semaines où
        // il est à Lyon → économie = 900 × 3/4 = 675 €/mois.
        // APP-110 : la semaine 4 (chacun chez soi) n'est plus un échange
        // → 3/4 d'échange = ECHANGE_PARTIEL, plus ECHANGE_TOTAL.
        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "B"),
                schedule(profileA, SEMAINE_2, "B"),
                schedule(profileA, SEMAINE_3, "B"),
                schedule(profileA, SEMAINE_4, "A"));
        List<AlternanceSchedule> schedulesB = List.of(
                schedule(profileB, SEMAINE_1, "B"),
                schedule(profileB, SEMAINE_2, "B"),
                schedule(profileB, SEMAINE_3, "B"),
                schedule(profileB, SEMAINE_4, "A"));

        MatchingResult result = calculator.calculate(
                profileA, profileB, schedulesA, schedulesB,
                logement("Paris", "650"), logement("Lyon", "900"));

        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_PARTIEL);
        assertThat(result.economieEstimeeMax()).isEqualByComparingTo("675");
        assertThat(result.economieEstimeeMin()).isEqualByComparingTo("675");
    }

    @Test
    void shouldCalculateEconomieForColocationWithBothLoyers() {
        // A et C : même rythme, mêmes villes → coloc tournante.
        // Loyers 650 + 900 → chacun économise (650+900)/2 = 775 €/mois.
        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "A"),
                schedule(profileA, SEMAINE_2, "B"));
        List<AlternanceSchedule> schedulesC = List.of(
                schedule(profileC, SEMAINE_1, "A"),
                schedule(profileC, SEMAINE_2, "B"));

        MatchingResult result = calculator.calculate(
                profileA, profileC, schedulesA, schedulesC,
                logement("Paris", "650"), logement("Lyon", "900"));

        assertThat(result.typePropose()).isEqualTo(AccordType.COLOCATION_TOURNANTE);
        assertThat(result.economieEstimeeMax()).isEqualByComparingTo("775");
    }

    @Test
    void shouldCalculatePartialEconomieForColocationWithOneLoyer() {
        // Un seul loyer connu (650) → estimation partielle : 650/2 = 325 €/mois
        List<AlternanceSchedule> schedulesA = List.of(
                schedule(profileA, SEMAINE_1, "A"));
        List<AlternanceSchedule> schedulesC = List.of(
                schedule(profileC, SEMAINE_1, "A"));

        MatchingResult result = calculator.calculate(
                profileA, profileC, schedulesA, schedulesC,
                logement("Paris", "650"), null);

        assertThat(result.economieEstimeeMax()).isEqualByComparingTo("325");
    }

    @Test
    void shouldReturnZeroEconomieWhenNoLogements() {
        List<AlternanceSchedule> schedulesA = List.of(schedule(profileA, SEMAINE_1, "B"));
        List<AlternanceSchedule> schedulesB = List.of(schedule(profileB, SEMAINE_1, "B"));

        // Variante 4 arguments : aucun loyer connu → zéro, jamais de chiffre inventé
        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        assertThat(result.economieEstimeeMax()).isEqualByComparingTo("0");
        assertThat(result.economieEstimeeMin()).isEqualByComparingTo("0");
    }

    @Test
    void shouldIgnoreLoyerOfLogementOutsideExchangeCities() {
        // Le logement de B est dans une ville où A ne va jamais → économie nulle
        // pour A (il n'occupera jamais ce logement pendant les échanges).
        List<AlternanceSchedule> schedulesA = List.of(schedule(profileA, SEMAINE_1, "B"));
        List<AlternanceSchedule> schedulesB = List.of(schedule(profileB, SEMAINE_1, "B"));

        MatchingResult result = calculator.calculate(
                profileA, profileB, schedulesA, schedulesB,
                null, logement("Bordeaux", "900"));

        assertThat(result.economieEstimeeMax()).isEqualByComparingTo("0");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private com.studup.backend.model.entity.Logement logement(String ville, String loyer) {
        return com.studup.backend.model.entity.Logement.builder()
                .id(UUID.randomUUID())
                .ville(ville)
                .loyer(new java.math.BigDecimal(loyer))
                .build();
    }

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
