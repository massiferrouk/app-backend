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

            CompatibiliteType type = classifierSemaine(villeA, villeB, profileA, profileB);

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
        double score = total > 0 ? (double) nbEchange / total : 0.0;
        score = Math.min(1.0, Math.round(score * 10000.0) / 10000.0);

        AccordType typePropose = determineAccordType(score, nbColocation);

        // Match actif = les deux alternants ont les logements nécessaires publiés
        // Pour l'instant on retourne false — sera enrichi dans MatchingService
        boolean isMatchActif = false;
        String messageMatchPotentiel = buildMessageMatchPotentiel(profileA, profileB, typePropose);

        // Économie mensuelle estimée du point de vue de A (APP-103).
        // min = ce qui est certain avec les loyers connus, max = identique
        // pour l'instant (fourchette réservée aux évolutions futures).
        BigDecimal economie = calculerEconomieMensuelle(
                typePropose, semaines, logementA, logementB);
        BigDecimal economieMin = economie;
        BigDecimal economieMax = economie;

        String messageResume = buildMessageResume(nbEchange, nbColocation, nbChevauchement, score, typePropose);

        return new MatchingResult(
                score,
                typePropose,
                isMatchActif,
                messageMatchPotentiel,
                semaines,
                nbEchange,
                nbColocation,
                nbChevauchement,
                economieMin,
                economieMax,
                messageResume
        );
    }

    // Classifie une semaine selon la position des deux alternants
    private CompatibiliteType classifierSemaine(String villeA, String villeB,
                                                 AlternantProfile profileA,
                                                 AlternantProfile profileB) {
        if (villeA.equalsIgnoreCase(villeB)) {
            // Même ville en même temps → colocation ou chevauchement (tranché au niveau du résultat global)
            return CompatibiliteType.COLOCATION;
        }

        // Villes différentes : échange possible si chacun est dans la ville de l'autre
        boolean aEstDansVilleDeB = villeA.equalsIgnoreCase(profileB.getVilleA())
                || villeA.equalsIgnoreCase(profileB.getVilleB());
        boolean bEstDansVilleDeA = villeB.equalsIgnoreCase(profileA.getVilleA())
                || villeB.equalsIgnoreCase(profileA.getVilleB());

        if (aEstDansVilleDeB && bEstDansVilleDeA) {
            return CompatibiliteType.ECHANGE;
        }

        return CompatibiliteType.INCOMPATIBLE;
    }

    // Mapping score → AccordType selon les seuils documentés
    private AccordType determineAccordType(double score, int nbColocation) {
        if (score >= 0.90) return AccordType.ECHANGE_TOTAL;
        if (score >= 0.60) return AccordType.ECHANGE_PARTIEL;
        if (score == 0.0 && nbColocation > 0) return AccordType.COLOCATION_TOURNANTE;
        return null;
    }

    private String buildMessageResume(int nbEchange, int nbColoc, int nbChevauchement,
                                       double score, AccordType type) {
        String base = nbEchange + " sem d'échange - " + nbColoc + " sem coloc - "
                + nbChevauchement + " sem chevauchement";

        if (type == AccordType.ECHANGE_TOTAL) {
            return base + "\nVos rythmes sont parfaitement complémentaires. Vous pouvez échanger vos logements sur toutes les semaines.";
        }
        if (type == AccordType.ECHANGE_PARTIEL) {
            int pct = (int) Math.round(score * 100);
            return base + "\nVos rythmes sont compatibles à " + pct + "%. " + nbEchange
                    + " semaines d'échange possibles. " + nbChevauchement + " semaine(s) à gérer entre vous.";
        }
        if (type == AccordType.COLOCATION_TOURNANTE) {
            return base + "\nVous avez exactement le même rythme. Vous pouvez partager un logement dans chaque ville.";
        }
        return base;
    }

    private String buildMessageMatchPotentiel(AlternantProfile a, AlternantProfile b, AccordType type) {
        if (type == AccordType.ECHANGE_TOTAL || type == AccordType.ECHANGE_PARTIEL) {
            return "Si vous publiez vos logements respectifs, vous pourrez faire un échange avec cet alternant.";
        }
        if (type == AccordType.COLOCATION_TOURNANTE) {
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
            long semainesChezB = semaines.stream()
                    .filter(s -> s.type() == CompatibiliteType.ECHANGE)
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
                List.of(), 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, "Aucune semaine commune");
    }
}
