package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.JourFerie;
import com.studup.backend.model.enums.PremiereSemaine;
import com.studup.backend.model.enums.RythmeAlternance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleGeneratorTest {

    private ScheduleGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ScheduleGenerator();
    }

    // Construit un profil de test avec les paramètres donnés
    private AlternantProfile buildProfile(LocalDate debut, LocalDate fin, RythmeAlternance rythme) {
        return AlternantProfile.builder()
                .id(UUID.randomUUID())
                .villeA("Paris")
                .villeB("Lyon")
                .ecole("ESIEA")
                .entreprise("Thales")
                .dateDebut(debut)
                .dateFin(fin)
                .rythme(rythme)
                .build();
    }

    // ─── Tests nombre de semaines générées ───────────────────────────────────

    @Test
    void shouldGenerate52WeeksForFullYear() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        assertThat(schedules).hasSize(52);
    }

    @Test
    void shouldStopBeforeDateFin() {
        // Contrat court : seulement 4 semaines
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2025, 9, 26),
                RythmeAlternance.SEMAINE_1_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        assertThat(schedules).hasSize(4);
        assertThat(schedules.getLast().getSemaine()).isBeforeOrEqualTo(LocalDate.of(2025, 9, 26));
    }

    @Test
    void shouldNeverExceed52Weeks() {
        // Contrat très long : 3 ans
        AlternantProfile profile = buildProfile(
                LocalDate.of(2023, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        assertThat(schedules).hasSizeLessThanOrEqualTo(52);
    }

    // ─── Tests labels selon le rythme ────────────────────────────────────────

    @Test
    void shouldAlternateLabelsSemaine11() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        // Semaine 0 = A, semaine 1 = B, semaine 2 = A...
        assertThat(schedules.get(0).getLabel()).isEqualTo("A");
        assertThat(schedules.get(1).getLabel()).isEqualTo("B");
        assertThat(schedules.get(2).getLabel()).isEqualTo("A");
        assertThat(schedules.get(3).getLabel()).isEqualTo("B");
    }

    @Test
    void shouldApplyCorrectPatternSemaine31() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_3_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        // Semaines 0,1,2 = B (entreprise), semaine 3 = A (école)
        assertThat(schedules.get(0).getLabel()).isEqualTo("B");
        assertThat(schedules.get(1).getLabel()).isEqualTo("B");
        assertThat(schedules.get(2).getLabel()).isEqualTo("B");
        assertThat(schedules.get(3).getLabel()).isEqualTo("A");
        // Le cycle recommence : semaine 4 = B
        assertThat(schedules.get(4).getLabel()).isEqualTo("B");
    }

    @Test
    void shouldApplyCorrectPatternMois11() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.MOIS_1_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        // Semaines 0-3 = A (école), semaines 4-7 = B (entreprise)
        for (int i = 0; i < 4; i++) {
            assertThat(schedules.get(i).getLabel()).isEqualTo("A");
        }
        for (int i = 4; i < 8; i++) {
            assertThat(schedules.get(i).getLabel()).isEqualTo("B");
        }
    }

    // ─── APP-110 : les 8 motifs rythme × première semaine (grille bloc 1) ────
    // Codes du document cas-de-test-matching.md — A = école (É), B = entreprise (E)

    // Concatène les labels des [nbSemaines] premières semaines en une chaîne
    private String motif(RythmeAlternance rythme, PremiereSemaine premiere, int nbSemaines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nbSemaines; i++) {
            sb.append(generator.getLabelForWeek(i, rythme, premiere));
        }
        return sb.toString();
    }

    @Test
    void shouldGenerateMotifA1_3EntreprisePuis1Ecole() {
        assertThat(motif(RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ENTREPRISE, 8))
                .isEqualTo("BBBABBBA");
    }

    @Test
    void shouldGenerateMotifA2_1EcolePuis3Entreprise() {
        assertThat(motif(RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ECOLE, 8))
                .isEqualTo("ABBBABBB");
    }

    @Test
    void shouldGenerateMotifB1_2EntreprisePuis2Ecole() {
        assertThat(motif(RythmeAlternance.SEMAINE_2_2, PremiereSemaine.ENTREPRISE, 8))
                .isEqualTo("BBAABBAA");
    }

    @Test
    void shouldGenerateMotifB2_2EcolePuis2Entreprise() {
        assertThat(motif(RythmeAlternance.SEMAINE_2_2, PremiereSemaine.ECOLE, 8))
                .isEqualTo("AABBAABB");
    }

    @Test
    void shouldGenerateMotifC1_1EntreprisePuis1Ecole() {
        assertThat(motif(RythmeAlternance.SEMAINE_1_1, PremiereSemaine.ENTREPRISE, 4))
                .isEqualTo("BABA");
    }

    @Test
    void shouldGenerateMotifC2_1EcolePuis1Entreprise() {
        assertThat(motif(RythmeAlternance.SEMAINE_1_1, PremiereSemaine.ECOLE, 4))
                .isEqualTo("ABAB");
    }

    @Test
    void shouldGenerateMotifD1_1MoisEntreprisePuis1MoisEcole() {
        assertThat(motif(RythmeAlternance.MOIS_1_1, PremiereSemaine.ENTREPRISE, 8))
                .isEqualTo("BBBBAAAA");
    }

    @Test
    void shouldGenerateMotifD2_1MoisEcolePuis1MoisEntreprise() {
        assertThat(motif(RythmeAlternance.MOIS_1_1, PremiereSemaine.ECOLE, 8))
                .isEqualTo("AAAABBBB");
    }

    @Test
    void shouldFallBackToLegacyOrderWhenPremiereSemaineIsNull() {
        // Profils d'avant la migration V24 : 3-1 commence entreprise, 1-1 école
        assertThat(motif(RythmeAlternance.SEMAINE_3_1, null, 4)).isEqualTo("BBBA");
        assertThat(motif(RythmeAlternance.SEMAINE_1_1, null, 4)).isEqualTo("ABAB");
        assertThat(motif(RythmeAlternance.MOIS_1_1, null, 8)).isEqualTo("AAAABBBB");
    }

    @Test
    void shouldUsePremiereSemaineFromProfile() {
        // Vérifie le branchement de bout en bout : profil → generateSchedule
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_2_2
        );
        profile.setPremiereSemaine(PremiereSemaine.ENTREPRISE);

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        assertThat(schedules.get(0).getLabel()).isEqualTo("B");
        assertThat(schedules.get(1).getLabel()).isEqualTo("B");
        assertThat(schedules.get(2).getLabel()).isEqualTo("A");
        assertThat(schedules.get(3).getLabel()).isEqualTo("A");
    }

    // ─── APP-110 cas 53 : rythme AUTRE explicitement annoté ──────────────────

    @Test
    void shouldAnnotateEveryAutreWeekAsDefaultCalendar() {
        // Le rythme AUTRE génère un 1/1 par défaut — mais plus en silence :
        // chaque semaine est annotée pour inviter à l'ajustement manuel
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.AUTRE
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        assertThat(schedules).allSatisfy(s ->
                assertThat(s.getOverrideReason())
                        .isEqualTo(ScheduleGenerator.RAISON_RYTHME_AUTRE)
        );
        // Le calendrier par défaut reste un 1/1 (A, B, A, B...)
        assertThat(schedules.get(0).getLabel()).isEqualTo("A");
        assertThat(schedules.get(1).getLabel()).isEqualTo("B");
    }

    @Test
    void shouldKeepHolidayAnnotationOverAutreAnnotation() {
        // Un jour férié est plus spécifique : il garde la priorité sur
        // l'annotation générique AUTRE
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.AUTRE
        );

        JourFerie armistice = JourFerie.builder()
                .id(UUID.randomUUID())
                .dateJour(LocalDate.of(2025, 11, 11))
                .libelle("Armistice")
                .pays("FR")
                .build();

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of(armistice));

        AlternanceSchedule semaineArmistice = schedules.stream()
                .filter(s -> s.getSemaine().equals(LocalDate.of(2025, 11, 10)))
                .findFirst()
                .orElseThrow();

        assertThat(semaineArmistice.getOverrideReason()).contains("2025-11-11");
    }

    // ─── Tests jours fériés ──────────────────────────────────────────────────

    @Test
    void shouldAnnotateWeekWithHoliday() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1
        );

        // Le 11 novembre 2025 (Armistice) est un mardi — tombe dans la semaine du lundi 10/11/2025
        JourFerie armistice = JourFerie.builder()
                .id(UUID.randomUUID())
                .dateJour(LocalDate.of(2025, 11, 11))
                .libelle("Armistice")
                .pays("FR")
                .build();

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of(armistice));

        // On trouve la semaine du 10/11/2025 et on vérifie qu'elle est annotée
        AlternanceSchedule semaineArmistice = schedules.stream()
                .filter(s -> s.getSemaine().equals(LocalDate.of(2025, 11, 10)))
                .findFirst()
                .orElseThrow();

        assertThat(semaineArmistice.getOverrideReason()).contains("2025-11-11");
    }

    @Test
    void shouldNotAnnotateWeekWithoutHoliday() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        // Sans jours fériés, toutes les overrideReason sont null
        assertThat(schedules).allSatisfy(s ->
                assertThat(s.getOverrideReason()).isNull()
        );
    }

    // ─── Tests structure des semaines ────────────────────────────────────────

    @Test
    void shouldAlwaysStartOnMonday() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 3), // mercredi
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        // Toutes les semaines doivent être des lundis
        assertThat(schedules).allSatisfy(s ->
                assertThat(s.getSemaine().getDayOfWeek().getValue()).isEqualTo(1)
        );
    }

    @Test
    void shouldSetIsOverriddenToFalse() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1
        );

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of());

        assertThat(schedules).allSatisfy(s ->
                assertThat(s.getIsOverridden()).isFalse()
        );
    }

    @Test
    void shouldHandleWeekendHolidayWithoutAnnotation() {
        AlternantProfile profile = buildProfile(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1
        );

        // Un jour férié un samedi ne doit pas annoter la semaine (on vérifie lundi-vendredi uniquement)
        JourFerie ferieWeekend = JourFerie.builder()
                .id(UUID.randomUUID())
                .dateJour(LocalDate.of(2025, 11, 8)) // samedi
                .libelle("Test samedi")
                .pays("FR")
                .build();

        List<AlternanceSchedule> schedules = generator.generateSchedule(profile, Set.of(ferieWeekend));

        // Aucune semaine ne devrait être annotée car le férié est un samedi
        assertThat(schedules).allSatisfy(s ->
                assertThat(s.getOverrideReason()).isNull()
        );
    }
}
