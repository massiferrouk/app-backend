package com.studup.backend.algorithm;

import com.studup.backend.algorithm.Scenario.ScenarioAction;
import com.studup.backend.algorithm.Scenario.ScenarioType;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.CompatibiliteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du moteur de scénarios (APP-109) — un test par scénario du
 * document de règles validé.
 */
class ScenarioAdvisorTest {

    private ScenarioAdvisor advisor;
    private AlternantProfile profileA; // moi : Paris ↔ Lyon
    private AlternantProfile profileB; // le match : Lyon ↔ Paris

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 7);

    @BeforeEach
    void setUp() {
        advisor = new ScenarioAdvisor();
        profileA = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(UUID.randomUUID()).firstName("Massi").build())
                .villeA("paris").villeB("lyon")
                .build();
        profileB = AlternantProfile.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(UUID.randomUUID()).firstName("Félix").build())
                .villeA("lyon").villeB("paris")
                .build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Logement logement(String ville, String loyer) {
        return Logement.builder()
                .id(UUID.randomUUID())
                .ville(ville)
                .loyer(loyer == null ? null : new BigDecimal(loyer))
                .build();
    }

    /** Résultat avec [nbEchange] semaines d'échange et [nbColocParis] de coloc à Paris */
    private MatchingResult result(AccordType type, int nbEchange, int nbColocParis) {
        var semaines = new java.util.ArrayList<SemaineCompatibilite>();
        for (int i = 0; i < nbEchange; i++) {
            semaines.add(SemaineCompatibilite.of(
                    LUNDI.plusWeeks(i), "paris", "lyon", CompatibiliteType.ECHANGE));
        }
        for (int i = 0; i < nbColocParis; i++) {
            semaines.add(SemaineCompatibilite.of(
                    LUNDI.plusWeeks(10 + i), "paris", "paris", CompatibiliteType.COLOCATION));
        }
        // potentiel = nbEchange : dans ces tests unitaires les semaines
        // d'échange simulées sont aussi des semaines potentielles
        return new MatchingResult(1.0, type, null, semaines,
                nbEchange, nbEchange, nbColocParis, 0, BigDecimal.ZERO, "");
    }

    // ─── S2 / S3 / S4 : logements manquants pour un échange ──────────────────

    @Test
    void shouldSuggestPublishingMyLogementWithCityName() {
        // B a publié à Lyon, moi rien → je dois publier à Paris (ville croisée)
        List<Scenario> scenarios = advisor.advise(
                result(AccordType.ECHANGE_TOTAL, 4, 0),
                profileA, profileB, null, logement("lyon", "550"));

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.TON_LOGEMENT_MANQUE);
        assertThat(scenarios.get(0).message()).contains("Paris");
        assertThat(scenarios.get(0).action()).isEqualTo(ScenarioAction.PUBLIER_LOGEMENT);
    }

    @Test
    void shouldSuggestContactingWhenHisLogementIsMissing() {
        List<Scenario> scenarios = advisor.advise(
                result(AccordType.ECHANGE_TOTAL, 4, 0),
                profileA, profileB, logement("paris", "700"), null);

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.SON_LOGEMENT_MANQUE);
        assertThat(scenarios.get(0).message()).contains("Félix");
        assertThat(scenarios.get(0).action()).isEqualTo(ScenarioAction.CONTACTER);
    }

    @Test
    void shouldSuggestBothPublishingWhenNoLogementAtAll() {
        List<Scenario> scenarios = advisor.advise(
                result(AccordType.ECHANGE_TOTAL, 4, 0),
                profileA, profileB, null, null);

        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.AUCUN_LOGEMENT);
        assertThat(scenarios.get(0).action()).isEqualTo(ScenarioAction.PUBLIER_LOGEMENT);
    }

    // ─── S6 : surplus même ville ──────────────────────────────────────────────

    @Test
    void shouldSuggestRelaisWhenInverseRythmsAndSameCitySurplus() {
        // Deux logements à Paris, rythmes inversés (jamais ensemble à Paris)
        List<Scenario> scenarios = advisor.advise(
                result(AccordType.ECHANGE_TOTAL, 4, 0),
                profileA, profileB, logement("paris", "700"), logement("paris", "550"));

        assertThat(scenarios).hasSize(2);
        // Priorité validée : RELAIS d'abord (zéro compromis)
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.RELAIS);
        assertThat(scenarios.get(0).message()).contains("vous ne vous y croisez jamais");
        // Économie certaine : partager le logement publié → mon loyer / 2 = 350
        assertThat(scenarios.get(0).economieMensuelle()).isEqualByComparingTo("350");
        // Le rééquilibrage mène à un échange (rythmes inversés)
        assertThat(scenarios.get(1).type()).isEqualTo(ScenarioType.REEQUILIBRER);
        assertThat(scenarios.get(1).message()).contains("échange total");
        assertThat(scenarios.get(1).message()).contains("Lyon");
        // APP-120 : aucun montant sur le rééquilibrage — il dépend d'un loyer
        // à Lyon que personne n'a publié. Les deux scénarios affichaient avant
        // le MÊME chiffre, dont un faux.
        assertThat(scenarios.get(1).economieMensuelle()).isEqualByComparingTo("0");
    }

    @Test
    void shouldSuggestReequilibrageAndColocUneVilleWhenSameRythm() {
        // Deux logements à Paris, parfois ensemble à Paris (semaines coloc)
        List<Scenario> scenarios = advisor.advise(
                result(AccordType.ECHANGE_PARTIEL, 2, 2),
                profileA, profileB, logement("paris", "700"), logement("paris", "550"));

        assertThat(scenarios).hasSize(2);
        assertThat(scenarios.get(0).type()).isEqualTo(ScenarioType.REEQUILIBRER);
        assertThat(scenarios.get(0).message()).contains("partagez les deux loyers");
        // APP-120 : gain inconnu (dépend d'un loyer à Lyon non publié)
        assertThat(scenarios.get(0).economieMensuelle()).isEqualByComparingTo("0");
        assertThat(scenarios.get(1).type()).isEqualTo(ScenarioType.COLOC_UNE_VILLE);
        assertThat(scenarios.get(1).message()).contains("Paris");
        assertThat(scenarios.get(1).message()).contains("Lyon");
        assertThat(scenarios.get(1).action()).isEqualTo(ScenarioAction.CONTACTER);
        // Celui-ci garde le logement publié à Paris → gain certain (700 / 2)
        assertThat(scenarios.get(1).economieMensuelle()).isEqualByComparingTo("350");
    }

    @Test
    void shouldReturnZeroEconomieWhenMyLoyerUnknownOnSurplus() {
        // Mon logement publié sans loyer → jamais de chiffre inventé
        List<Scenario> scenarios = advisor.advise(
                result(AccordType.ECHANGE_TOTAL, 4, 0),
                profileA, profileB, logement("paris", null), logement("paris", "550"));

        assertThat(scenarios).isNotEmpty();
        assertThat(scenarios.get(0).economieMensuelle()).isEqualByComparingTo("0");
    }

    // ─── Cas sans scénario ────────────────────────────────────────────────────

    @Test
    void shouldReturnEmptyWhenMatchIsReady() {
        // Échange classique complet : logements croisés, rien à suggérer
        List<Scenario> scenarios = advisor.advise(
                result(AccordType.ECHANGE_TOTAL, 4, 0),
                profileA, profileB, logement("paris", "700"), logement("lyon", "550"));

        assertThat(scenarios).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoCompatibility() {
        List<Scenario> scenarios = advisor.advise(
                result(null, 0, 0),
                profileA, profileB, null, null);

        assertThat(scenarios).isEmpty();
    }
}
