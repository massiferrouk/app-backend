package com.studup.backend.model.dto.response;

import com.studup.backend.algorithm.ColocationProposal;
import com.studup.backend.algorithm.SemaineCompatibilite;

import java.math.BigDecimal;
import java.util.List;

public record ColocationResponse(
        List<SemaineCompatibilite> semainesColocation,
        int nbSemainesColocation,
        String villeColocationPrincipale,
        BigDecimal economieMensuelle,
        BigDecimal economieTotaleEstimee,
        String messageResume
) {
    public static ColocationResponse from(ColocationProposal proposal) {
        return new ColocationResponse(
                proposal.semainesColocation(),
                proposal.nbSemainesColocation(),
                proposal.villeColocationPrincipale(),
                proposal.economieMensuelle(),
                proposal.economieTotaleEstimee(),
                proposal.messageResume()
        );
    }
}
