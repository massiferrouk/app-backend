package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.MotInterdit;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Un mot filtré par la modération de la messagerie (APP-121). */
public record MotInterditResponse(
        UUID id,
        String mot,
        OffsetDateTime createdAt
) {
    public static MotInterditResponse from(MotInterdit mot) {
        return new MotInterditResponse(mot.getId(), mot.getMot(), mot.getCreatedAt());
    }
}
