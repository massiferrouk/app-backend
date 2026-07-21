package com.studup.backend.algorithm;

import com.studup.backend.model.enums.AccordType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résultat du calcul de compatibilité entre deux alternants.
 *
 * Ne contient QUE ce que l'algorithme sait déduire des deux calendriers et des
 * loyers. Savoir si un match est « actif » (les logements nécessaires sont
 * publiés) demande une vue sur les logements des deux côtés : c'est le rôle du
 * MatchingService, pas du calculateur.
 */
public record MatchingResult(
        double score,
        AccordType typePropose,
        String messageMatchPotentiel,
        List<SemaineCompatibilite> semaines,
        int nbSemainesEchange,
        // Semaines aux positions croisées où un échange SERAIT possible avec
        // les bons logements publiés (APP-110). nbSemainesEchange ne compte que
        // les échanges réels (logements publiés, chacun chez l'autre) —
        // le potentiel sert aux scénarios conditionnels et à la visibilité
        // des matchs potentiels.
        int nbSemainesEchangePotentiel,
        int nbSemainesColocation,
        int nbSemainesChevauchement,
        BigDecimal economieMensuelle,
        String messageResume
) {}
