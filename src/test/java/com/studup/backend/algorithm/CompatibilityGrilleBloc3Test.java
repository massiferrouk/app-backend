package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.CompatibiliteType;
import com.studup.backend.model.enums.PremiereSemaine;
import com.studup.backend.model.enums.RythmeAlternance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du BLOC 3 de la grille cas-de-test-matching.md (APP-110) :
 * les cas de bord — décalages de dates de début, recouvrement partiel ou nul,
 * overrides manuels, une seule ou aucune ville commune.
 * (Le cas 53 — rythme AUTRE annoté — est testé dans ScheduleGeneratorTest.)
 *
 * Setup type bloc 1 : A1 × A1 (3-1, entreprise d'abord), villes croisées,
 * logements en ville d'études — seules les DATES varient selon les cas.
 */
class CompatibilityGrilleBloc3Test {

    private ScheduleGenerator generator;
    private CompatibilityCalculator calculator;

    private Logement logementMassi;   // Bordeaux
    private Logement logementFelix;   // Paris

    private static final LocalDate LUNDI_1 = LocalDate.of(2026, 9, 7);

    @BeforeEach
    void setUp() {
        generator = new ScheduleGenerator();
        calculator = new CompatibilityCalculator();
        logementMassi = Logement.builder().id(UUID.randomUUID()).ville("Bordeaux").build();
        logementFelix = Logement.builder().id(UUID.randomUUID()).ville("Paris").build();
    }

    private AlternantProfile profil(String villeEtudes, String villeEntreprise,
                                    LocalDate debut, LocalDate fin) {
        return AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA(villeEtudes).villeB(villeEntreprise)
                .dateDebut(debut).dateFin(fin)
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .premiereSemaine(PremiereSemaine.ENTREPRISE) // A1
                .build();
    }

    private MatchingResult calculer(AlternantProfile massi, AlternantProfile felix) {
        List<AlternanceSchedule> schedulesMassi = generator.generateSchedule(massi, Set.of());
        List<AlternanceSchedule> schedulesFelix = generator.generateSchedule(felix, Set.of());
        return calculator.calculate(massi, felix, schedulesMassi, schedulesFelix,
                logementMassi, logementFelix);
    }

    // ─── Cas 49 : Félix démarre 1 semaine plus tard ──────────────────────────

    @Test
    void cas49_decalageUneSemaine_repartitionChange() {
        // Alignés, A1×A1 donne 3é/1n par cycle. Décalés d'une semaine, les
        // cycles glissent : la répartition devient 2é/2c — l'alignement doit
        // se faire sur les DATES réelles, pas sur les index de semaine.
        AlternantProfile massi = profil("Bordeaux", "Paris",
                LUNDI_1, LUNDI_1.plusWeeks(8).plusDays(6));                 // 9 semaines
        AlternantProfile felix = profil("Paris", "Bordeaux",
                LUNDI_1.plusWeeks(1), LUNDI_1.plusWeeks(8).plusDays(6));    // 8 semaines

        MatchingResult result = calculer(massi, felix);

        // 8 semaines communes = 2 cycles décalés → 4 échanges + 4 colocs, 0 neutre
        assertThat(result.semaines()).hasSize(8);
        assertThat(result.nbSemainesEchange()).isEqualTo(4);
        assertThat(result.nbSemainesColocation()).isEqualTo(4);
    }

    // ─── Cas 50 : décalage de 2 semaines — un autre calage encore ────────────

    @Test
    void cas50_decalageDeuxSemaines_autreCalage() {
        AlternantProfile massi = profil("Bordeaux", "Paris",
                LUNDI_1, LUNDI_1.plusWeeks(8).plusDays(6));                 // 9 semaines
        AlternantProfile felix = profil("Paris", "Bordeaux",
                LUNDI_1.plusWeeks(2), LUNDI_1.plusWeeks(8).plusDays(6));    // 7 semaines

        MatchingResult result = calculer(massi, felix);

        // 7 semaines communes : é/c/é/c/é/c/é → 4 échanges + 3 colocs
        // Même couple de rythmes, 3e calage, 3e répartition — CQFD
        assertThat(result.semaines()).hasSize(7);
        assertThat(result.nbSemainesEchange()).isEqualTo(4);
        assertThat(result.nbSemainesColocation()).isEqualTo(3);
    }

    // ─── Cas 51 : recouvrement partiel — seule l'intersection compte ─────────

    @Test
    void cas51_recouvrementPartiel_seuleIntersectionComparee() {
        // Massi : 8 semaines à partir du 07/09. Félix : 12 semaines à partir
        // du 05/10. Intersection : du 05/10 au 01/11 → 4 semaines seulement.
        AlternantProfile massi = profil("Bordeaux", "Paris",
                LUNDI_1, LocalDate.of(2026, 11, 1));
        AlternantProfile felix = profil("Paris", "Bordeaux",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 12, 27));

        MatchingResult result = calculer(massi, felix);

        assertThat(result.semaines()).hasSize(4);
        // Toutes les semaines comparées sont dans l'intersection
        assertThat(result.semaines()).allSatisfy(s -> {
            assertThat(s.semaine()).isAfterOrEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(s.semaine()).isBeforeOrEqualTo(LocalDate.of(2026, 11, 1));
        });
        // Massi entame son 2e cycle (position 0) quand Félix démarre :
        // même phase → 3 échanges + 1 « chacun chez soi »
        assertThat(result.nbSemainesEchange()).isEqualTo(3);
        assertThat(result.nbSemainesColocation()).isZero();
    }

    // ─── Cas 52 : aucun recouvrement — aucune suggestion ─────────────────────

    @Test
    void cas52_aucunRecouvrement_aucuneSuggestion() {
        AlternantProfile massi = profil("Bordeaux", "Paris",
                LUNDI_1, LocalDate.of(2026, 10, 4));
        AlternantProfile felix = profil("Paris", "Bordeaux",
                LocalDate.of(2027, 1, 4), LocalDate.of(2027, 6, 27));

        MatchingResult result = calculer(massi, felix);

        assertThat(result.score()).isZero();
        assertThat(result.typePropose()).isNull();
        assertThat(result.semaines()).isEmpty();
        assertThat(result.messageResume()).isEqualTo("Aucune semaine commune");
    }

    // ─── Cas 54 : overrides manuels — le calendrier MODIFIÉ fait foi ─────────

    @Test
    void cas54_overrideManuel_compatibiliteRecalculeeSurCalendrierModifie() {
        AlternantProfile massi = profil("Bordeaux", "Paris",
                LUNDI_1, LocalDate.of(2026, 11, 1));
        AlternantProfile felix = profil("Paris", "Bordeaux",
                LUNDI_1, LocalDate.of(2026, 11, 1));

        List<AlternanceSchedule> schedulesMassi = generator.generateSchedule(massi, Set.of());
        List<AlternanceSchedule> schedulesFelix = generator.generateSchedule(felix, Set.of());

        // Baseline A1×A1 : 6 échanges, 0 coloc, 2 neutres
        MatchingResult avant = calculator.calculate(massi, felix,
                schedulesMassi, schedulesFelix, logementMassi, logementFelix);
        assertThat(avant.nbSemainesEchange()).isEqualTo(6);
        assertThat(avant.nbSemainesColocation()).isZero();

        // Massi modifie manuellement sa 4e semaine : école → entreprise
        // (il reste à Paris cette semaine-là au lieu de rentrer à Bordeaux)
        AlternanceSchedule semaineModifiee = schedulesMassi.get(3);
        semaineModifiee.setLabel("B");
        semaineModifiee.setIsOverridden(true);
        semaineModifiee.setOverrideReason("Semaine de projet sur site");

        MatchingResult apres = calculator.calculate(massi, felix,
                schedulesMassi, schedulesFelix, logementMassi, logementFelix);

        // Cette semaine-là Félix est à l'école à Paris : ils se retrouvent
        // dans la même ville → une COLOC apparaît, un neutre disparaît
        assertThat(apres.nbSemainesEchange()).isEqualTo(6);
        assertThat(apres.nbSemainesColocation()).isEqualTo(1);
        assertThat(apres.semaines().get(3).type()).isEqualTo(CompatibiliteType.COLOCATION);
    }

    // ─── Cas 55 : une seule ville commune — hors scope V1, documenté ─────────

    @Test
    void cas55_uneSeuleVilleCommune_aucunMatchEnV1() {
        // Massi Bordeaux ⇄ Paris, Félix Lyon ⇄ Bordeaux : seule Bordeaux est
        // commune. Décision produit : hors scope V1 (hébergement asymétrique
        // documenté pour V2). Avec des cycles alignés ils ne sont jamais dans
        // la même ville en même temps → aucun match proposé, pas de crash.
        AlternantProfile massi = profil("Bordeaux", "Paris",
                LUNDI_1, LocalDate.of(2026, 11, 1));
        AlternantProfile felix = profil("Lyon", "Bordeaux",
                LUNDI_1, LocalDate.of(2026, 11, 1));
        logementFelix = Logement.builder().id(UUID.randomUUID()).ville("Lyon").build();

        MatchingResult result = calculer(massi, felix);

        assertThat(result.typePropose()).isNull();
        assertThat(result.nbSemainesEchange()).isZero();
        assertThat(result.nbSemainesEchangePotentiel()).isZero();
        assertThat(result.nbSemainesColocation()).isZero();
    }

    // ─── Cas 56 : aucune ville commune — jamais comparés ─────────────────────

    @Test
    void cas56_aucuneVilleCommune_aucuneCompatibilite() {
        // La présélection (findCandidatesWithSharedCity) empêche déjà cette
        // comparaison en production ; si elle arrivait quand même, le
        // calculateur ne doit rien proposer.
        AlternantProfile massi = profil("Bordeaux", "Paris",
                LUNDI_1, LocalDate.of(2026, 11, 1));
        AlternantProfile felix = profil("Lyon", "Marseille",
                LUNDI_1, LocalDate.of(2026, 11, 1));
        logementFelix = Logement.builder().id(UUID.randomUUID()).ville("Lyon").build();

        MatchingResult result = calculer(massi, felix);

        assertThat(result.typePropose()).isNull();
        assertThat(result.score()).isZero();
        assertThat(result.semaines())
                .allSatisfy(s -> assertThat(s.type())
                        .isEqualTo(CompatibiliteType.INCOMPATIBLE));
    }
}
