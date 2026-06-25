package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.Review;
import com.studup.backend.model.enums.ReviewTargetType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID authorId,
        UUID targetUserId,
        UUID targetLogementId,
        UUID accordId,
        ReviewTargetType targetType,
        Integer rating,
        String comment,
        Boolean isModerated,
        OffsetDateTime createdAt
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getAuthorId(),
                r.getTargetUserId(),
                r.getTargetLogementId(),
                r.getAccordId(),
                r.getTargetType(),
                r.getRating(),
                r.getComment(),
                r.getIsModerated(),
                r.getCreatedAt()
        );
    }
}
