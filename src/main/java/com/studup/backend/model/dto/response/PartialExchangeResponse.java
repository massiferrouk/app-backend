package com.studup.backend.model.dto.response;

import com.studup.backend.algorithm.PartialExchangeProposal;
import com.studup.backend.algorithm.SemaineCompatibilite;

import java.math.BigDecimal;
import java.util.List;

public record PartialExchangeResponse(
        List<SemaineCompatibilite> semainesProposees,
        int nbSemainesEchange,
        int nbSemainesChevauchement,
        BigDecimal economieTotale,
        String messageResume
) {
    public static PartialExchangeResponse from(PartialExchangeProposal proposal) {
        return new PartialExchangeResponse(
                proposal.semainesProposees(),
                proposal.nbSemainesEchange(),
                proposal.nbSemainesChevauchement(),
                proposal.economieTotale(),
                proposal.messageResume()
        );
    }
}
