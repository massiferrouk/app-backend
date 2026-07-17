package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.PremiereSemaine;
import com.studup.backend.model.enums.RythmeAlternance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du BLOC 1 de la grille cas-de-test-matching.md (APP-110) :
 * croisements de rythmes en bout en bout — le VRAI ScheduleGenerator alimente
 * le VRAI CompatibilityCalculator, sur le setup fixe de la grille :
 * Massi études Bordeaux ⇄ entreprise Paris, Félix études Paris ⇄ entreprise
 * Bordeaux, mêmes dates, 8 semaines (2 cycles de 4 ou 1 cycle de 8).
 *
 * Les répartitions attendues (échange / coloc / neutre) sont celles calculées
 * à la main dans le document, doublées pour 8 semaines.
 */
class CompatibilityGrilleBloc1Test {

    private ScheduleGenerator generator;
    private CompatibilityCalculator calculator;

    private AlternantProfile massi;   // études Bordeaux, entreprise Paris
    private AlternantProfile felix;   // études Paris, entreprise Bordeaux

    // Setup grille : chacun un logement publié dans sa ville d'études
    private Logement logementMassi;   // Bordeaux
    private Logement logementFelix;   // Paris

    // 8 semaines pleines : lundi 07/09/2026 → dimanche 01/11/2026
    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 7);
    private static final LocalDate FIN = LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setUp() {
        generator = new ScheduleGenerator();
        calculator = new CompatibilityCalculator();

        massi = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Bordeaux").villeB("Paris")
                .dateDebut(DEBUT).dateFin(FIN)
                .build();

        felix = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Paris").villeB("Bordeaux")
                .dateDebut(DEBUT).dateFin(FIN)
                .build();

        logementMassi = Logement.builder()
                .id(UUID.randomUUID())
                .ville("Bordeaux")
                .build();

        logementFelix = Logement.builder()
                .id(UUID.randomUUID())
                .ville("Paris")
                .build();
    }

    // Génère les calendriers, lance le calcul et vérifie la répartition attendue
    private MatchingResult croiser(RythmeAlternance rythmeMassi, PremiereSemaine premMassi,
                                   RythmeAlternance rythmeFelix, PremiereSemaine premFelix) {
        massi.setRythme(rythmeMassi);
        massi.setPremiereSemaine(premMassi);
        felix.setRythme(rythmeFelix);
        felix.setPremiereSemaine(premFelix);

        List<AlternanceSchedule> schedulesMassi = generator.generateSchedule(massi, Set.of());
        List<AlternanceSchedule> schedulesFelix = generator.generateSchedule(felix, Set.of());

        return calculator.calculate(massi, felix, schedulesMassi, schedulesFelix,
                logementMassi, logementFelix);
    }

    private void verifierRepartition(MatchingResult result,
                                     int echange, int coloc, int neutre) {
        assertThat(result.nbSemainesEchange()).as("semaines d'échange").isEqualTo(echange);
        assertThat(result.nbSemainesColocation()).as("semaines de coloc").isEqualTo(coloc);
        int neutresCalculees = result.semaines().size() - result.nbSemainesEchange()
                - result.nbSemainesColocation();
        assertThat(neutresCalculees).as("semaines neutres").isEqualTo(neutre);
        assertThat(result.semaines()).as("8 semaines communes").hasSize(8);
    }

    // ─── Diagonale : même rythme, même ordre → échange dominant ──────────────

    @Test
    void cas01_A1xA1_memeRythmeMemeOrdre_echange75pct() {
        // Grille n°1 : 3é / 0c / 1n par cycle de 4 → 6 / 0 / 2 sur 8 semaines
        MatchingResult result = croiser(
                RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ENTREPRISE);
        verifierRepartition(result, 6, 0, 2);
    }

    @Test
    void cas16_B1xB1_memeRythmeMemeOrdre_echange50pct() {
        // Grille n°16 : 2é / 0c / 2n par cycle → 4 / 0 / 4
        MatchingResult result = croiser(
                RythmeAlternance.SEMAINE_2_2, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.SEMAINE_2_2, PremiereSemaine.ENTREPRISE);
        verifierRepartition(result, 4, 0, 4);
    }

    @Test
    void cas27_C1xC1_memeRythmeMemeOrdre_echange50pct() {
        // Grille n°27 : 1é / 0c / 1n par cycle de 2 → 4 / 0 / 4
        MatchingResult result = croiser(
                RythmeAlternance.SEMAINE_1_1, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.SEMAINE_1_1, PremiereSemaine.ENTREPRISE);
        verifierRepartition(result, 4, 0, 4);
    }

    @Test
    void cas34_D1xD1_memeRythmeMemeOrdre_echange50pct() {
        // Grille n°34 : 4é / 0c / 4n sur le cycle de 8
        MatchingResult result = croiser(
                RythmeAlternance.MOIS_1_1, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.MOIS_1_1, PremiereSemaine.ENTREPRISE);
        verifierRepartition(result, 4, 0, 4);
    }

    // ─── Ordres inversés → coloc tournante 100 % (sauf 3-1, asymétrique) ─────

    @Test
    void cas17_B1xB2_ordresInverses_colocTournante100pct() {
        // Grille n°17 : toujours dans la même ville en même temps
        MatchingResult result = croiser(
                RythmeAlternance.SEMAINE_2_2, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.SEMAINE_2_2, PremiereSemaine.ECOLE);
        verifierRepartition(result, 0, 8, 0);
    }

    @Test
    void cas28_C1xC2_ordresInverses_colocTournante100pct() {
        // Grille n°28
        MatchingResult result = croiser(
                RythmeAlternance.SEMAINE_1_1, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.SEMAINE_1_1, PremiereSemaine.ECOLE);
        verifierRepartition(result, 0, 8, 0);
    }

    @Test
    void cas35_D1xD2_ordresInverses_colocTournante100pct() {
        // Grille n°35
        MatchingResult result = croiser(
                RythmeAlternance.MOIS_1_1, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.MOIS_1_1, PremiereSemaine.ECOLE);
        verifierRepartition(result, 0, 8, 0);
    }

    @Test
    void cas02_A1xA2_ordresInverses_maisMixte5050_carAsymetrique() {
        // Grille n°2 — le piège : le 3-1 n'est pas symétrique, l'inversion
        // ne les rend pas complémentaires → 2é / 2c par cycle, pas 100 % coloc
        MatchingResult result = croiser(
                RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ECOLE);
        verifierRepartition(result, 4, 4, 0);
    }

    // ─── Croisements mixtes ──────────────────────────────────────────────────

    @Test
    void cas04_A1xB2_mixteDominanteColoc() {
        // Grille n°4 : 1é / 3c / 0n par cycle → 2 / 6 / 0
        MatchingResult result = croiser(
                RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.SEMAINE_2_2, PremiereSemaine.ECOLE);
        verifierRepartition(result, 2, 6, 0);
    }

    @Test
    void cas07_A1xD1_mixteEquilibre() {
        // Grille n°7 : 3é / 4c / 1n sur le cycle de 8
        MatchingResult result = croiser(
                RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ENTREPRISE,
                RythmeAlternance.MOIS_1_1, PremiereSemaine.ENTREPRISE);
        verifierRepartition(result, 3, 4, 1);
    }
}
