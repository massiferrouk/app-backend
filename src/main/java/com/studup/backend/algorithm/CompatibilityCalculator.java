package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.CompatibiliteType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Component
public class CompatibilityCalculator {

    /**
     * Variante sans logements : l'économie estimée reste à zéro.
     * Utilisée quand les loyers ne sont pas nécessaires (notifications de
     * match, calendrier) ou pas encore connus.
     */
    public MatchingResult calculate(AlternantProfile profileA,
                                    AlternantProfile profileB,
                                    List<AlternanceSchedule> schedulesA,
                                    List<AlternanceSchedule> schedulesB) {
        return calculate(profileA, profileB, schedulesA, schedulesB, null, null);
    }

    /**
     * Calcule la compatibilité entre deux alternants.
     * Prérequis : les deux profils doivent avoir au moins une ville en commun (vérifié avant l'appel).
     *
     * [logementA] / [logementB] : logements PUBLIÉS de chaque alternant
     * (null si pas encore publié) — servent au calcul de l'économie estimée
     * du point de vue de l'alternant A (APP-103).
     */
    public MatchingResult calculate(AlternantProfile profileA,
                                    AlternantProfile profileB,
                                    List<AlternanceSchedule> schedulesA,
                                    List<AlternanceSchedule> schedulesB,
                                    Logement logementA,
                                    Logement logementB) {

        if (schedulesA.isEmpty() || schedulesB.isEmpty()) {
            return emptyResult();
        }

        // Index les schedules par date de semaine pour accès O(1)
        Map<LocalDate, String> mapA = indexByDate(schedulesA);
        Map<LocalDate, String> mapB = indexByDate(schedulesB);

        // Période commune = intersection des semaines des deux alternants
        Set<LocalDate> semainesCommunes = new HashSet<>(mapA.keySet());
        semainesCommunes.retainAll(mapB.keySet());

        if (semainesCommunes.isEmpty()) {
            return emptyResult();
        }

        List<SemaineCompatibilite> semaines = new ArrayList<>();
        int nbEchange = 0;
        int nbEchangePotentiel = 0;
        int nbColocation = 0;
        int nbChevauchement = 0;

        // Compteur par ville pour détecter les semaines récurrentes de colocation
        Map<String, Integer> colocParVille = new HashMap<>();

        for (LocalDate semaine : semainesCommunes.stream().sorted().toList()) {
            String labelA = mapA.get(semaine);  // "A" ou "B"
            String labelB = mapB.get(semaine);

            // Ville où se trouve chaque alternant cette semaine
            String villeA = "A".equals(labelA) ? profileA.getVilleA() : profileA.getVilleB();
            String villeB = "A".equals(labelB) ? profileB.getVilleA() : profileB.getVilleB();

            CompatibiliteType type;
            if (villeA.equalsIgnoreCase(villeB)) {
                // Même ville en même temps : fait de position, vrai quels que
                // soient les logements
                type = CompatibiliteType.COLOCATION;
            } else if (positionsCroisees(villeA, villeB, profileA, profileB)) {
                // Un échange SERAIT possible ici avec les bons logements
                nbEchangePotentiel++;
                // Échange RÉEL uniquement si chacun dort dans la ville du
                // logement publié de l'autre (règle §3 grille APP-110) —
                // sans logements publiés, on n'affirme rien : semaine neutre,
                // les scénarios portent le conditionnel.
                type = estEchangeReel(villeA, villeB, logementA, logementB)
                        ? CompatibiliteType.ECHANGE
                        : CompatibiliteType.INCOMPATIBLE;
            } else {
                type = CompatibiliteType.INCOMPATIBLE;
            }

            semaines.add(SemaineCompatibilite.of(semaine, villeA, villeB, type));

            switch (type) {
                case ECHANGE -> nbEchange++;
                case COLOCATION -> {
                    nbColocation++;
                    colocParVille.merge(villeA, 1, Integer::sum);
                }
                case CHEVAUCHEMENT -> nbChevauchement++;
                default -> {}
            }
        }

        int total = semaines.size();
        // Score = semaines où StudUp fait économiser (échange OU coloc) sur le
        // total. Avant APP-108, seul l'échange comptait : les cas mixtes
        // (rythmes différents, villes communes) tombaient sous le seuil et
        // devenaient invisibles alors qu'ils sont les plus fréquents.
        // Depuis APP-110, seul l'échange RÉEL compte : pas de logements
        // publiés = score honnête, sans hypothèse silencieuse.
        double score = total > 0 ? (double) (nbEchange + nbColocation) / total : 0.0;
        score = Math.min(1.0, Math.round(score * 10000.0) / 10000.0);

        // Type proposé : basé sur le réel quand les deux logements sont
        // publiés ; sinon sur les positions (potentiel), pour que les matchs
        // potentiels restent visibles et notifiés — l'app informe, elle ne
        // décide pas à la place de l'utilisateur (APP-110).
        boolean logementsConnus = logementA != null && logementB != null;
        AccordType typePropose = logementsConnus
                ? determineAccordType(nbEchange, nbColocation, total)
                : determineAccordType(nbEchangePotentiel, nbColocation, total);

        // Match actif = les deux alternants ont les logements nécessaires publiés
        // Pour l'instant on retourne false — sera enrichi dans MatchingService
        boolean isMatchActif = false;
        String messageMatchPotentiel = buildMessageMatchPotentiel(
                typePropose, logementA, logementB);

        // Économie mensuelle estimée du point de vue de A (APP-103).
        // min = ce qui est certain avec les loyers connus, max = identique
        // pour l'instant (fourchette réservée aux évolutions futures).
        BigDecimal economie = calculerEconomieMensuelle(
                typePropose, semaines, logementA, logementB);
        BigDecimal economieMin = economie;
        BigDecimal economieMax = economie;

        int nbChacunChezSoi = total - nbEchange - nbColocation;
        String messageResume = buildMessageResume(
                nbEchange, nbColocation, nbChacunChezSoi, score, typePropose);

        return new MatchingResult(
                score,
                typePropose,
                isMatchActif,
                messageMatchPotentiel,
                semaines,
                nbEchange,
                nbEchangePotentiel,
                nbColocation,
                nbChevauchement,
                economieMin,
                economieMax,
                messageResume
        );
    }

    // Positions croisées : chacun est dans une des villes de l'autre cette semaine
    private boolean positionsCroisees(String villeA, String villeB,
                                      AlternantProfile profileA,
                                      AlternantProfile profileB) {
        boolean aEstDansVilleDeB = villeA.equalsIgnoreCase(profileB.getVilleA())
                || villeA.equalsIgnoreCase(profileB.getVilleB());
        boolean bEstDansVilleDeA = villeB.equalsIgnoreCase(profileA.getVilleA())
                || villeB.equalsIgnoreCase(profileA.getVilleB());
        return aEstDansVilleDeB && bEstDansVilleDeA;
    }

    /**
     * Échange RÉEL (règle §3 de la grille, APP-110) : les deux logements sont
     * publiés, dans deux villes différentes, et chacun passe cette semaine
     * dans la ville du logement de l'autre. C'est la seule situation où
     * quelqu'un dort effectivement chez l'autre — une semaine « chacun chez
     * soi » (chacun dans la ville de son propre logement) n'est PAS un échange.
     */
    private boolean estEchangeReel(String villeA, String villeB,
                                   Logement logementA, Logement logementB) {
        if (logementA == null || logementB == null) return false;
        String villeLogementA = logementA.getVille();
        String villeLogementB = logementB.getVille();
        if (villeLogementA == null || villeLogementB == null) return false;
        // Deux logements dans la même ville ne s'échangent pas (surplus, APP-109)
        if (villeLogementA.equalsIgnoreCase(villeLogementB)) return false;
        return villeA.equalsIgnoreCase(villeLogementB)
                && villeB.equalsIgnoreCase(villeLogementA);
    }

    /**
     * Mapping vers AccordType (APP-108). On raisonne sur les nombres de
     * semaines, pas sur un seuil de score :
     * - 100 % d'échange → ECHANGE_TOTAL
     * - au moins une semaine d'échange (mais pas 100 %) → ECHANGE_PARTIEL,
     *   les semaines de coloc sont incluses dans le message
     * - aucune semaine d'échange mais de la coloc → COLOCATION_TOURNANTE
     * - ni échange ni coloc → pas de match
     */
    private AccordType determineAccordType(int nbEchange, int nbColocation, int total) {
        if (total == 0) return null;
        if (nbEchange == total) return AccordType.ECHANGE_TOTAL;
        if (nbEchange > 0) return AccordType.ECHANGE_PARTIEL;
        if (nbColocation > 0) return AccordType.COLOCATION_TOURNANTE;
        return null;
    }

    private String buildMessageResume(int nbEchange, int nbColoc, int nbChacunChezSoi,
                                       double score, AccordType type) {
        String base = nbEchange + " sem d'échange - " + nbColoc + " sem coloc - "
                + nbChacunChezSoi + " sem chacun chez soi";

        if (type == AccordType.ECHANGE_TOTAL) {
            return base + "\nVos rythmes sont parfaitement complémentaires. Vous pouvez échanger vos logements sur toutes les semaines.";
        }
        if (type == AccordType.ECHANGE_PARTIEL) {
            int pct = (int) Math.round(score * 100);
            String coloc = nbColoc > 0
                    ? " et faire coloc " + nbColoc + " semaine(s)"
                    : "";
            return base + "\nVous pouvez économiser sur " + pct + "% des semaines : "
                    + nbEchange + " semaine(s) d'échange" + coloc + ".";
        }
        if (type == AccordType.COLOCATION_TOURNANTE) {
            return base + "\nVous avez le même rythme. Vous pouvez partager un logement et diviser les loyers.";
        }
        return base;
    }

    private String buildMessageMatchPotentiel(AccordType type,
                                              Logement logementA, Logement logementB) {
        if (type == AccordType.ECHANGE_TOTAL || type == AccordType.ECHANGE_PARTIEL) {
            return "Si vous publiez vos logements respectifs, vous pourrez faire un échange avec cet alternant.";
        }
        if (type == AccordType.COLOCATION_TOURNANTE) {
            // Cas 47 de la grille : personne n'a de logement → « lâcher son
            // logement » n'a pas de sens, on propose d'en trouver un à deux
            if (logementA == null && logementB == null) {
                return "Vous avez le même rythme. Trouvez un logement à deux et divisez le loyer.";
            }
            return "Si l'un de vous lâche son logement, vous pourrez partager les logements et diviser les loyers.";
        }
        return null;
    }

    /**
     * Économie mensuelle moyenne pour l'alternant A, en euros entiers (APP-103).
     *
     * ÉCHANGE : les semaines où A occupe le logement de B (A est dans la ville
     * du logement de B pendant une semaine d'échange), il ne paie pas ce loyer.
     * Moyenne mensuelle = loyerB × (semaines bénéficiaires / semaines totales).
     *
     * COLOCATION TOURNANTE : les deux partagent les logements, chacun paie la
     * moitié de chaque loyer → économie = (loyerA + loyerB) / 2.
     * Un seul loyer connu = estimation partielle (moitié du loyer connu).
     *
     * Aucun loyer exploitable → ZERO (le frontend n'affiche rien).
     */
    private BigDecimal calculerEconomieMensuelle(AccordType type,
                                                 List<SemaineCompatibilite> semaines,
                                                 Logement logementA,
                                                 Logement logementB) {
        if (type == null || semaines.isEmpty()) return BigDecimal.ZERO;

        if (type == AccordType.ECHANGE_TOTAL || type == AccordType.ECHANGE_PARTIEL) {
            if (logementB == null || logementB.getLoyer() == null) {
                return BigDecimal.ZERO;
            }
            // Semaines où A loge dans le logement de B, sans payer de loyer :
            // - échange : B est ailleurs, son logement est libre
            // - coloc : ils y sont ensemble, A ne prend pas un 2e logement
            // Dans les deux cas A évite un loyer dans la ville du logement de B (APP-108).
            long semainesChezB = semaines.stream()
                    .filter(s -> s.type() == CompatibiliteType.ECHANGE
                            || s.type() == CompatibiliteType.COLOCATION)
                    .filter(s -> s.villeAlternantA()
                            .equalsIgnoreCase(logementB.getVille()))
                    .count();
            return logementB.getLoyer()
                    .multiply(BigDecimal.valueOf(semainesChezB))
                    .divide(BigDecimal.valueOf(semaines.size()), 0, RoundingMode.HALF_UP);
        }

        if (type == AccordType.COLOCATION_TOURNANTE) {
            BigDecimal loyerA = logementA == null ? null : logementA.getLoyer();
            BigDecimal loyerB = logementB == null ? null : logementB.getLoyer();
            if (loyerA == null && loyerB == null) return BigDecimal.ZERO;
            BigDecimal somme = (loyerA == null ? BigDecimal.ZERO : loyerA)
                    .add(loyerB == null ? BigDecimal.ZERO : loyerB);
            return somme.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    private Map<LocalDate, String> indexByDate(List<AlternanceSchedule> schedules) {
        Map<LocalDate, String> map = new HashMap<>();
        for (AlternanceSchedule s : schedules) {
            map.put(s.getSemaine(), s.getLabel());
        }
        return map;
    }

    private MatchingResult emptyResult() {
        return new MatchingResult(0.0, null, false, null,
                List.of(), 0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, "Aucune semaine commune");
    }
}
