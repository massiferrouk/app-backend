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
        int nbSemainesColocation,
        int nbSemainesChevauchement,
        BigDecimal economieEstimeeMin,
        BigDecimal economieEstimeeMax,
        String messageResume
) {}
