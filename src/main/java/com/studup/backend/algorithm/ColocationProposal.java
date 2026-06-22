package com.studup.backend.algorithm;

import java.math.BigDecimal;
import java.util.List;

public record ColocationProposal(
        List<SemaineCompatibilite> semainesColocation,
        int nbSemainesColocation,
        String villeColocationPrincipale,
        BigDecimal economieMensuelle,
        BigDecimal economieTotaleEstimee,
        String messageResume
) {}
