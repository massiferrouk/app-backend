package com.studup.backend.algorithm;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résultat de l'optimisation d'un échange partiel.
 * Contient uniquement les semaines d'échange possibles (couleur VERT).
 */
public record PartialExchangeProposal(
        List<SemaineCompatibilite> semainesProposees,
        int nbSemainesEchange,
        int nbSemainesChevauchement,
        BigDecimal economieTotale,
        String messageResume
) {
}
