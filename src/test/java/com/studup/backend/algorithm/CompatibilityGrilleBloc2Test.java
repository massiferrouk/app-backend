package com.studup.backend.algorithm;

import com.studup.backend.algorithm.Scenario.ScenarioType;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.AccordType;
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
 * Tests du BLOC 2 de la grille cas-de-test-matching.md (APP-110) :
 * les 12 configurations de logement, cas 37 à 48.
 *
 * Setup fixe de la grille : rythme A1 × A1 (3-1, première semaine entreprise),
 * mêmes dates, 8 semaines. Villes croisées par défaut (Massi études Bordeaux ⇄
 * entreprise Paris, Félix l'inverse) ; « mêmes villes » quand le cas le précise.
 *
 * États : L0 = aucun logement · L1 = logement ville d'études ·
 * L2 = logement ville d'entreprise.
 */
class CompatibilityGrilleBloc2Test {

    private ScheduleGenerator generator;
    private CompatibilityCalculator calculator;
    private ScenarioAdvisor advisor;

    private AlternantProfile massi;   // études Bordeaux, entreprise Paris
    private AlternantProfile felix;   // croisé (défaut) ou mêmes villes selon le cas

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 7);
    private static final LocalDate FIN = LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setUp() {
        generator = new ScheduleGenerator();
        calculator = new CompatibilityCalculator();
        advisor = new ScenarioAdvisor();

        massi = profil("Bordeaux", "Paris");
        felix = profil("Paris", "Bordeaux"); // villes croisées par défaut
    }

    private AlternantProfile profil(String villeEtudes, String villeEntreprise) {
        return AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA(villeEtudes).villeB(villeEntreprise)
                .dateDebut(DEBUT).dateFin(FIN)
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .premiereSemaine(PremiereSemaine.ENTREPRISE) // A1
                .build();
    }

    private Logement logement(String ville, String loyer) {
        return Logement.builder()
                .id(UUID.randomUUID())
                .ville(ville)
                .loyer(loyer == null ? null : new BigDecimal(loyer))
                .build();
    }

    private MatchingResult calculer(Logement logementMassi, Logement logementFelix) {
        List<AlternanceSchedule> schedulesMassi = generator.generateSchedule(massi, Set.of());
        List<AlternanceSchedule> schedulesFelix = generator.generateSchedule(felix, Set.of());
        return calculator.calculate(massi, felix, schedulesMassi, schedulesFelix,
                logementMassi, logementFelix);
    }

    private List<Scenario> scenarios(MatchingResult result,
                                     Logement logementMassi, Logement logementFelix) {
        return advisor.advise(result, massi, felix, logementMassi, logementFelix);
    }

    // ─── Cas 37 : L0 / L0, villes croisées ───────────────────────────────────

    @Test
    void cas37_aucunLogement_matchPotentielSansChiffreInvente() {
        MatchingResult result = calculer(null, null);

        // Potentiel visible (positions 100 % croisées) mais rien d'affirmé :
        // score réel nul, aucune économie affichée
        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_TOTAL);
        assertThat(result.nbSemainesEchange()).isZero();
        assertThat(result.nbSemainesEchangePotentiel()).isEqualTo(8);
        assertThat(result.score()).isZero();
        assertThat(result.economieMensuelle()).isEqualByComparingTo("0");

        // Scénario « publier » pour les deux
        List<Scenario> scenarios = scenarios(result, null, null);
        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.AUCUN_LOGEMENT);
        assertThat(scenarios.get(0).economieMensuelle()).isEqualByComparingTo("0");
    }

    // ─── Cas 38 : L0 / L1 — le CTA nomme la ville complémentaire ─────────────

    @Test
    void cas38_monLogementManque_ctaAvecVilleComplementaire() {
        Logement logementFelix = logement("Paris", "800"); // L1 : sa ville d'études

        MatchingResult result = calculer(null, logementFelix);
        List<Scenario> scenarios = scenarios(result, null, logementFelix);

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.TON_LOGEMENT_MANQUE);
        // Félix loge à Paris → la ville qui me manque est Bordeaux
        assertThat(scenarios.get(0).message()).contains("Bordeaux");
    }

    // ─── Cas 39 : L0 / L2 — la ville du LOGEMENT prime sur la ville d'études ─

    @Test
    void cas39_logementVilleEntreprise_scenarioUtiliseVilleDuLogement() {
        // Félix loge dans sa ville d'ENTREPRISE (Bordeaux), pas d'études
        Logement logementFelix = logement("Bordeaux", "800");

        MatchingResult result = calculer(null, logementFelix);
        List<Scenario> scenarios = scenarios(result, null, logementFelix);

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.TON_LOGEMENT_MANQUE);
        // Son logement est à Bordeaux → je dois publier à Paris —
        // preuve que le scénario lit la ville du logement, pas la ville d'études
        assertThat(scenarios.get(0).message()).contains("Paris");
    }

    // ─── Cas 40 : L1 / L1 — le cas nominal du bloc 1 ─────────────────────────

    @Test
    void cas40_deuxLogementsVilleEtudes_echange75AvecEconomie() {
        Logement logementMassi = logement("Bordeaux", "700");
        Logement logementFelix = logement("Paris", "800");

        MatchingResult result = calculer(logementMassi, logementFelix);

        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_PARTIEL);
        assertThat(result.score()).isEqualTo(0.75);
        assertThat(result.nbSemainesEchange()).isEqualTo(6);
        // Économie de Massi : loyer de Félix (800, Paris) × 6/8 = 600 €/mois
        assertThat(result.economieMensuelle()).isEqualByComparingTo("600");

        // Match prêt : aucun scénario conditionnel nécessaire
        assertThat(scenarios(result, logementMassi, logementFelix)).isEmpty();
    }

    // ─── Cas 41 : L1 / L2 — surplus même ville, échange impossible ───────────

    @Test
    void cas41_deuxLogementsMemeVille_surplusRelais() {
        Logement logementMassi = logement("Bordeaux", "700");  // L1
        Logement logementFelix = logement("Bordeaux", "650");  // L2 : sa ville entreprise

        MatchingResult result = calculer(logementMassi, logementFelix);

        // Deux logements à Bordeaux ne s'échangent pas : zéro semaine réelle
        assertThat(result.nbSemainesEchange()).isZero();
        assertThat(result.typePropose()).isNull();

        // A1×A1 croisés : jamais dans la même ville → RELAIS prioritaire
        List<Scenario> scenarios = scenarios(result, logementMassi, logementFelix);
        assertThat(scenarios).extracting(Scenario::type).containsExactly(
                ScenarioType.RELAIS, ScenarioType.REEQUILIBRER);
        // Économie certaine du relais : la moitié de MON loyer (700/2)
        assertThat(scenarios.get(0).economieMensuelle()).isEqualByComparingTo("350");
    }

    // ─── Cas 42 : L2 / L2 — le motif s'inverse ───────────────────────────────

    @Test
    void cas42_logementsVilleEntreprise_echangeSurSemainesEcole() {
        Logement logementMassi = logement("Paris", "900");     // sa ville entreprise
        Logement logementFelix = logement("Bordeaux", "650");  // sa ville entreprise

        MatchingResult result = calculer(logementMassi, logementFelix);

        // En semaine entreprise chacun est CHEZ LUI → neutre.
        // L'échange bascule sur les 2 semaines école du cycle : motif inversé.
        assertThat(result.typePropose()).isEqualTo(AccordType.ECHANGE_PARTIEL);
        assertThat(result.nbSemainesEchange()).isEqualTo(2);
        assertThat(result.score()).isEqualTo(0.25);
        // Les semaines d'échange sont bien les semaines ÉCOLE de Massi (Bordeaux)
        assertThat(result.semaines())
                .filteredOn(s -> s.type() == CompatibiliteType.ECHANGE)
                .allSatisfy(s -> assertThat(s.villeAlternantA()).isEqualTo("Bordeaux"));
    }

    // ─── Cas 43 : L1 / L1, mêmes villes — coloc + surplus ────────────────────

    @Test
    void cas43_memesVillesDeuxLogementsBordeaux_colocEtSurplus() {
        felix = profil("Bordeaux", "Paris"); // mêmes villes que Massi
        Logement logementMassi = logement("Bordeaux", "700");
        Logement logementFelix = logement("Bordeaux", "650");

        MatchingResult result = calculer(logementMassi, logementFelix);

        // Toujours dans la même ville en même temps → 8 semaines de coloc
        assertThat(result.typePropose()).isEqualTo(AccordType.COLOCATION_TOURNANTE);
        assertThat(result.nbSemainesColocation()).isEqualTo(8);

        // Surplus même ville, mais ils SONT ensemble à Bordeaux (semaines école)
        // → rééquilibrer ou coloc une ville, pas de relais
        List<Scenario> scenarios = scenarios(result, logementMassi, logementFelix);
        assertThat(scenarios).extracting(Scenario::type).containsExactly(
                ScenarioType.REEQUILIBRER, ScenarioType.COLOC_UNE_VILLE);
    }

    // ─── Cas 44 : L0 / L1, mêmes villes — publier ne débloque PAS d'échange ──

    @Test
    void cas44_memesVillesUnSeulLogement_pasDeScenarioEchange() {
        felix = profil("Bordeaux", "Paris"); // mêmes villes
        Logement logementFelix = logement("Bordeaux", "650");

        MatchingResult result = calculer(null, logementFelix);

        // Positions jamais croisées → zéro échange, même potentiel
        assertThat(result.nbSemainesEchangePotentiel()).isZero();
        assertThat(result.typePropose()).isEqualTo(AccordType.COLOCATION_TOURNANTE);
        // Le message parle de coloc, pas d'échange
        assertThat(result.messageMatchPotentiel()).doesNotContain("échange");

        // Et surtout : aucun CTA « publie ton logement pour un échange »
        List<Scenario> scenarios = scenarios(result, null, logementFelix);
        assertThat(scenarios).extracting(Scenario::type)
                .doesNotContain(ScenarioType.TON_LOGEMENT_MANQUE,
                        ScenarioType.AUCUN_LOGEMENT);
    }

    // ─── Cas 45 : L2 / L1, mêmes villes — configuration riche ────────────────

    @Test
    void cas45_memesVillesLogementsComplementaires_colocTournante() {
        felix = profil("Bordeaux", "Paris"); // mêmes villes
        Logement logementMassi = logement("Paris", "900");    // sa ville entreprise
        Logement logementFelix = logement("Bordeaux", "650"); // sa ville d'études

        MatchingResult result = calculer(logementMassi, logementFelix);

        // Un logement par ville + toujours ensemble → vraie coloc tournante :
        // partage des deux logements, loyers divisés par deux
        assertThat(result.typePropose()).isEqualTo(AccordType.COLOCATION_TOURNANTE);
        assertThat(result.nbSemainesColocation()).isEqualTo(8);
        assertThat(result.economieMensuelle()).isEqualByComparingTo("775"); // (900+650)/2
    }

    // ─── Cas 46 : logement dans une 3ᵉ ville — rien d'absurde ────────────────

    @Test
    void cas46_logementVilleTierce_aucuneSuggestionAbsurde() {
        Logement logementMassi = logement("Bordeaux", "700");
        Logement logementFelix = logement("Lille", "500"); // ni études ni entreprise

        MatchingResult result = calculer(logementMassi, logementFelix);

        // Personne ne va jamais à Lille : aucun échange réel, aucune économie
        assertThat(result.nbSemainesEchange()).isZero();
        assertThat(result.typePropose()).isNull();
        assertThat(result.economieMensuelle()).isEqualByComparingTo("0");
        assertThat(scenarios(result, logementMassi, logementFelix)).isEmpty();
    }

    // ─── Cas 47 : L0 / L0, mêmes villes — « trouvez un logement à deux » ─────

    @Test
    void cas47_memesVillesAucunLogement_messageTrouverADeux() {
        felix = profil("Bordeaux", "Paris"); // mêmes villes

        MatchingResult result = calculer(null, null);

        assertThat(result.typePropose()).isEqualTo(AccordType.COLOCATION_TOURNANTE);
        // Personne n'a de logement : le message ne dit pas « lâche ton
        // logement » mais « trouvez un logement à deux »
        assertThat(result.messageMatchPotentiel()).contains("Trouvez un logement à deux");
    }

    // ─── Cas 48 : L2 / L0 — le message nomme la ville complémentaire ─────────

    @Test
    void cas48_sonLogementManque_messageAvecVilleComplementaire() {
        // Massi loge dans sa ville d'ENTREPRISE (Paris), Félix n'a rien
        Logement logementMassi = logement("Paris", "900");

        MatchingResult result = calculer(logementMassi, null);
        List<Scenario> scenarios = scenarios(result, logementMassi, null);

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.SON_LOGEMENT_MANQUE);
        // Mon logement est à Paris → il manque un logement à Bordeaux,
        // la ville complémentaire au logement EXISTANT
        assertThat(scenarios.get(0).message()).contains("Bordeaux");
    }
}
