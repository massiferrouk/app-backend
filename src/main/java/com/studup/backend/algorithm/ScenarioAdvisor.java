package com.studup.backend.algorithm;

import com.studup.backend.algorithm.Scenario.ScenarioAction;
import com.studup.backend.algorithm.Scenario.ScenarioType;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.CompatibiliteType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Moteur de scénarios (APP-109) : croise les positions semaine par semaine
 * (MatchingResult) avec l'inventaire des logements publiés, et produit la
 * liste des arrangements possibles avec leurs messages.
 *
 * La liste est triée par priorité d'affichage : le premier élément est le
 * scénario « principal » montré sur la match card ; l'écran Compatibilité
 * affiche la liste complète (« Vos options »).
 *
 * Règle absolue : jamais de chiffre inventé — l'économie d'un scénario ne
 * s'appuie que sur des loyers réellement publiés.
 */
@Component
public class ScenarioAdvisor {

    /**
     * [logementA] = logement publié de l'utilisateur connecté (null si aucun),
     * [logementB] = celui du candidat. Perspective : les messages tutoient A.
     */
    public List<Scenario> advise(MatchingResult result,
                                 AlternantProfile profileA,
                                 AlternantProfile profileB,
                                 Logement logementA,
                                 Logement logementB) {
        // Pas de compatibilité du tout (ni échange possible ni coloc) → rien.
        // On ne se fie pas qu'au typePropose : depuis APP-110 il peut être null
        // alors qu'un surplus même ville reste à proposer (cas 41 de la grille).
        if (result.typePropose() == null
                && result.nbSemainesEchangePotentiel() == 0
                && result.nbSemainesColocation() == 0) {
            return List.of();
        }

        // ─── S6 : surplus même ville — prioritaire sur tout le reste ────
        if (estSurplusMemeVille(logementA, logementB)) {
            return scenariosSurplus(result, profileA, logementA);
        }

        // ─── S2/S3/S4 : logements manquants pour un échange ─────────────
        // Potentiel, pas réel : ces scénarios expliquent justement quoi
        // publier pour transformer le potentiel en échange réel (APP-110)
        if (result.nbSemainesEchangePotentiel() > 0) {
            if (logementA == null && logementB != null) {
                return List.of(tonLogementManque(profileA, logementB));
            }
            if (logementA != null && logementB == null) {
                return List.of(sonLogementManque(profileB, logementA));
            }
            if (logementA == null) { // les deux manquent
                return List.of(new Scenario(
                        ScenarioType.AUCUN_LOGEMENT,
                        "Publiez chacun votre logement pour débloquer cet échange.",
                        BigDecimal.ZERO,
                        ScenarioAction.PUBLIER_LOGEMENT));
            }
        }

        // Match prêt (échange ou coloc avec l'inventaire qu'il faut) :
        // l'affichage standard suffit, pas de scénario conditionnel.
        return List.of();
    }

    // ─── S6 : surplus même ville ──────────────────────────────────────────────

    /** Les deux logements publiés sont dans la même ville */
    private boolean estSurplusMemeVille(Logement logementA, Logement logementB) {
        return logementA != null && logementB != null
                && logementA.getVille() != null && logementB.getVille() != null
                && logementA.getVille().equalsIgnoreCase(logementB.getVille());
    }

    /**
     * Variantes du surplus (document APP-109) :
     * - jamais ensemble à V1 (rythmes inversés) → RELAIS + REEQUILIBRER
     *   (le rééquilibrage mène alors à un échange total, le message s'adapte)
     * - parfois ensemble à V1 → REEQUILIBRER (vers coloc) + COLOC_UNE_VILLE
     * Ordre de la liste = priorité d'affichage validée : S6c > S6a > S6b.
     */
    private List<Scenario> scenariosSurplus(MatchingResult result,
                                            AlternantProfile profileA,
                                            Logement logementA) {
        String v1 = capitalize(logementA.getVille());
        String v2 = capitalize(autreVille(profileA, logementA.getVille()));
        BigDecimal economie = moitieLoyer(logementA);

        long semainesEnsembleV1 = result.semaines().stream()
                .filter(s -> s.type() == CompatibiliteType.COLOCATION)
                .filter(s -> s.villeAlternantA().equalsIgnoreCase(logementA.getVille()))
                .count();

        List<Scenario> scenarios = new ArrayList<>();
        if (semainesEnsembleV1 == 0) {
            scenarios.add(new Scenario(
                    ScenarioType.RELAIS,
                    "Vos rythmes sont inversés : en gardant un seul logement à "
                            + v1 + ", vous ne vous y croiserez jamais. Un loyer "
                            + "divisé par deux, et chacun l'a pour lui seul.",
                    economie,
                    ScenarioAction.CONTACTER));
            scenarios.add(new Scenario(
                    ScenarioType.REEQUILIBRER,
                    "Vous avez tous les deux un logement à " + v1 + " mais aucun à "
                            + v2 + ". Si l'un de vous lâche le sien et que vous "
                            + "trouvez un logement à " + v2
                            + ", vous pourrez faire un échange total.",
                    economie,
                    ScenarioAction.CONTACTER));
        } else {
            scenarios.add(new Scenario(
                    ScenarioType.REEQUILIBRER,
                    "Vous avez tous les deux un logement à " + v1 + " mais aucun à "
                            + v2 + ". Si l'un de vous lâche le sien et que vous "
                            + "trouvez un logement à " + v2
                            + ", vous partagez les deux loyers.",
                    economie,
                    ScenarioAction.CONTACTER));
            scenarios.add(new Scenario(
                    ScenarioType.COLOC_UNE_VILLE,
                    "Gardez un seul logement partagé à " + v1 + " et cherchez "
                            + "chacun votre logement à " + v2 + " : indépendance là "
                            + "où vous passez le plus de temps, loyer divisé là où "
                            + "vous ne faites que passer.",
                    economie,
                    ScenarioAction.CONTACTER));
        }
        return scenarios;
    }

    // ─── S2 / S3 ──────────────────────────────────────────────────────────────

    private Scenario tonLogementManque(AlternantProfile profileA, Logement logementB) {
        // La ville où publier = ma ville qui n'est pas celle du logement de B
        // (pour un échange croisé, chacun publie dans une ville différente)
        String villeManquante = capitalize(autreVille(profileA, logementB.getVille()));
        return new Scenario(
                ScenarioType.TON_LOGEMENT_MANQUE,
                "Publie ton logement à " + villeManquante
                        + " pour débloquer cet échange.",
                BigDecimal.ZERO,
                ScenarioAction.PUBLIER_LOGEMENT);
    }

    private Scenario sonLogementManque(AlternantProfile profileB, Logement logementA) {
        String prenom = profileB.getUser() != null
                ? profileB.getUser().getFirstName()
                : "Ton match";
        // La ville qui manque = la ville complémentaire au logement existant
        // (cas 48 de la grille : le message doit refléter la bonne ville,
        // celle du LOGEMENT publié, pas la ville d'études par principe)
        String villeManquante = capitalize(autreVille(profileB, logementA.getVille()));
        return new Scenario(
                ScenarioType.SON_LOGEMENT_MANQUE,
                prenom + " n'a pas encore publié son logement. S'il en trouve un à "
                        + villeManquante + ", vous pourrez faire un échange. "
                        + "Contacte-le pour en discuter.",
                BigDecimal.ZERO,
                ScenarioAction.CONTACTER);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** La ville du profil qui n'est PAS [ville] (villeA sinon villeB) */
    private String autreVille(AlternantProfile profile, String ville) {
        return profile.getVilleA().equalsIgnoreCase(ville)
                ? profile.getVilleB()
                : profile.getVilleA();
    }

    /** Économie certaine des scénarios S6 : la moitié de MON loyer publié */
    private BigDecimal moitieLoyer(Logement logement) {
        if (logement == null || logement.getLoyer() == null) return BigDecimal.ZERO;
        return logement.getLoyer().divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
    }

    /** Les villes arrivent souvent en minuscules — première lettre en capitale */
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
