package com.studup.backend.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AlternantDashboardResponse(
        List<AccordSummaryResponse> prochainAccords,
        List<AccordSummaryResponse> accordsEnAttente,
        BigDecimal economiesEstimees,
        int nbAccordsTermines
) {}
