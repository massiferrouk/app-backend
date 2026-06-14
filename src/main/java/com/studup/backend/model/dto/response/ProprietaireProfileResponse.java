package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.ProprietaireProfile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProprietaireProfileResponse(
        UUID id,
        UUID userId,
        String phone,
        String adresse,
        String ville,
        String codePostal,
        String siret,
        Boolean isVerified,
        OffsetDateTime createdAt
) {
    public static ProprietaireProfileResponse from(ProprietaireProfile profile) {
        return new ProprietaireProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getPhone(),
                profile.getAdresse(),
                profile.getVille(),
                profile.getCodePostal(),
                profile.getSiret(),
                profile.getIsVerified(),
                profile.getCreatedAt()
        );
    }
}
