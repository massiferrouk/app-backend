package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.enums.CompatibiliteType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Détecte et quantifie les opportunités de colocation tournante entre deux alternants.
 * Délègue le calcul semaine par semaine au CompatibilityCalculator,
 * puis extrait uniquement les semaines COLOCATION (même ville en même temps).
 */
@Component
public class ColocationMatcher {

    // 4.33 semaines en moyenne par mois (52 / 12)
    private static final BigDecimal SEMAINES_PAR_MOIS = new BigDecimal("4.33");

    private final CompatibilityCalculator calculator;

    public ColocationMatcher(CompatibilityCalculator calculator) {
        this.calculator = calculator;
    }

    /**
     * Retourne la proposition de colocation entre deux alternants.
     *
     * @param loyerVilleA loyer mensuel du logement en villeA (null = non calculé)
     * @param loyerVilleB loyer mensuel du logement en villeB (null = non calculé)
     */
    public ColocationProposal match(
            AlternantProfile profileA,
            AlternantProfile profileB,
            List<AlternanceSchedule> schedulesA,
            List<AlternanceSchedule> schedulesB,
            BigDecimal loyerVilleA,
            BigDecimal loyerVilleB) {

        MatchingResult result = calculator.calculate(profileA, profileB, schedulesA, schedulesB);

        // Filtre uniquement les semaines de colocation (même ville en même temps)
        List<SemaineCompatibilite> semainesColoc = result.semaines().stream()
                .filter(s -> s.type() == CompatibiliteType.COLOCATION)
                .toList();

        int nbColoc = semainesColoc.size();
        String villePrincipale = detecterVillePrincipale(semainesColoc);
        BigDecimal economieMensuelle = calculerEconomieMensuelle(loyerVilleA, loyerVilleB);
        BigDecimal economieTotale = calculerEconomieTotale(economieMensuelle, nbColoc);
        String message = construireMessage(nbColoc, villePrincipale, economieMensuelle, economieTotale);

        return new ColocationProposal(
                semainesColoc,
                nbColoc,
                villePrincipale,
                economieMensuelle,
                economieTotale,
                message
        );
    }

    /**
     * Détecte la ville la plus fréquente dans les semaines de colocation.
     * Flutter l'affiche comme titre de la proposition.
     */
    private String detecterVillePrincipale(List<SemaineCompatibilite> semaines) {
        if (semaines.isEmpty()) return null;

        return semaines.stream()
                .collect(Collectors.groupingBy(
                        SemaineCompatibilite::villeAlternantA,
                        Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Économie mensuelle = (loyerVilleA + loyerVilleB) / 2
     * Chaque alternant paie moitié au lieu de payer deux loyers pleins.
     */
    private BigDecimal calculerEconomieMensuelle(BigDecimal loyerVilleA, BigDecimal loyerVilleB) {
        if (loyerVilleA == null && loyerVilleB == null) return BigDecimal.ZERO;

        BigDecimal loyerA = loyerVilleA != null ? loyerVilleA : BigDecimal.ZERO;
        BigDecimal loyerB = loyerVilleB != null ? loyerVilleB : BigDecimal.ZERO;

        return loyerA.add(loyerB)
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Économie totale estimée sur toute la période de colocation.
     * = économie mensuelle × (nbSemainesColoc / 4.33)
     */
    private BigDecimal calculerEconomieTotale(BigDecimal economieMensuelle, int nbSemaines) {
        if (economieMensuelle == null || economieMensuelle.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(nbSemaines)
                .divide(SEMAINES_PAR_MOIS, 2, RoundingMode.HALF_UP)
                .multiply(economieMensuelle)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String construireMessage(int nbColoc, String ville, BigDecimal economieMensuelle,
                                     BigDecimal economieTotale) {
        if (nbColoc == 0) {
            return "Aucune semaine de colocation détectée.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(nbColoc).append(" sem. de colocation possible");

        if (ville != null) {
            sb.append(" (principalement à ").append(ville).append(")");
        }

        if (economieMensuelle != null && economieMensuelle.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" — économie : ").append(economieMensuelle).append(" €/mois");
        }

        if (economieTotale != null && economieTotale.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(", soit ").append(economieTotale).append(" € sur la période");
        }

        return sb.toString();
    }
}
