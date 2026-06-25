package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.ReputationScore;
import com.studup.backend.service.ReputationService;

import java.math.BigDecimal;
import java.util.UUID;

public record ReputationScoreResponse(
        UUID userId,
        BigDecimal avgRating,
        Integer totalReviews,
        BigDecimal logementScore,
        Integer nbAccords,
        String badge
) {
    public static ReputationScoreResponse from(ReputationScore s) {
        BigDecimal avg = s.getAvgRating() != null ? s.getAvgRating() : BigDecimal.ZERO;
        int total = s.getTotalReviews() != null ? s.getTotalReviews() : 0;
        return new ReputationScoreResponse(
                s.getUserId(),
                avg,
                total,
                s.getLogementScore() != null ? s.getLogementScore() : BigDecimal.ZERO,
                s.getNbAccords() != null ? s.getNbAccords() : 0,
                ReputationService.calculateBadge(avg, total)
        );
    }
}
