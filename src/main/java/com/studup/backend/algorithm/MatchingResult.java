package com.studup.backend.algorithm;

import com.studup.backend.model.enums.AccordType;

import java.math.BigDecimal;
import java.util.List;

public record MatchingResult(
        double score,
        AccordType typePropose,
        boolean isMatchActif,
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
        BigDecimal economieEstimeeMin,
        BigDecimal economieEstimeeMax,
        String messageResume
) {}
