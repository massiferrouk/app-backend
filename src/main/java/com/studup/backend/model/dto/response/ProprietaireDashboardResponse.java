package com.studup.backend.model.dto.response;

import java.util.List;

public record ProprietaireDashboardResponse(
        int nbLogementsTotaux,
        int nbLogementsActifs,
        int nbLocatairesActifs,
        double tauxOccupation,
        List<LogementSummaryResponse> logements
) {}
