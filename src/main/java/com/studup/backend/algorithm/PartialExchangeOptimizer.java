package com.studup.backend.algorithm;

import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.CompatibiliteType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Optimise les semaines d'échange partiel entre deux alternants.
 * Extrait uniquement les semaines VERT (ECHANGE) depuis le résultat du CompatibilityCalculator.
 * Les semaines de chevauchement (ORANGE) sont exclues de la proposition — à gérer entre les deux utilisateurs.
 */
@Component
public class PartialExchangeOptimizer {

    // 4.33 semaines en moyenne par mois (52 semaines / 12 mois)
    private static final BigDecimal SEMAINES_PAR_MOIS = new BigDecimal("4.33");

    private final CompatibilityCalculator calculator;

    public PartialExchangeOptimizer(CompatibilityCalculator calculator) {
        this.calculator = calculator;
    }

    /**
     * Retourne les semaines d'échange possibles entre deux alternants
     * et l'économie estimée si un loyer mensuel est fourni.
     *
     * [logementA] / [logementB] : logements publiés — sans eux, plus aucune
     * semaine d'échange réelle n'existe (règle §3, APP-110).
     *
     * @param loyerMensuel loyer mensuel de référence (null = économie non calculée)
     */
    public PartialExchangeProposal optimize(
            AlternantProfile profileA,
            AlternantProfile profileB,
            List<AlternanceSchedule> schedulesA,
            List<AlternanceSchedule> schedulesB,
            Logement logementA,
            Logement logementB,
            BigDecimal loyerMensuel) {

        MatchingResult result = calculator.calculate(profileA, profileB,
                schedulesA, schedulesB, logementA, logementB);

        // Filtre uniquement les semaines d'échange (VERT)
        List<SemaineCompatibilite> semainesEchange = result.semaines().stream()
                .filter(s -> s.type() == CompatibiliteType.ECHANGE)
                .toList();

        int nbEchange = semainesEchange.size();
        int nbChevauchement = result.nbSemainesChevauchement();

        BigDecimal economie = calculerEconomie(nbEchange, loyerMensuel);
        String message = construireMessage(nbEchange, nbChevauchement, economie);

        return new PartialExchangeProposal(semainesEchange, nbEchange, nbChevauchement, economie, message);
    }

    /**
     * Économie = (nbSemainesEchange / 4.33) × loyerMensuel
     * Représente le nombre de mois d'échange × le loyer évité.
     */
    private BigDecimal calculerEconomie(int nbSemainesEchange, BigDecimal loyerMensuel) {
        if (loyerMensuel == null || loyerMensuel.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(nbSemainesEchange)
                .divide(SEMAINES_PAR_MOIS, 2, RoundingMode.HALF_UP)
                .multiply(loyerMensuel)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String construireMessage(int nbEchange, int nbChevauchement, BigDecimal economie) {
        StringBuilder sb = new StringBuilder();
        sb.append(nbEchange).append(" sem. d'échange proposées");

        if (nbChevauchement > 0) {
            sb.append(" — ").append(nbChevauchement).append(" sem. à gérer entre vous");
        }

        if (economie != null && economie.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" — économie estimée : ").append(economie).append(" €");
        }

        return sb.toString();
    }
}
