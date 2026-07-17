package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.enums.PremiereSemaine;
import com.studup.backend.model.enums.RythmeAlternance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AlternantProfileResponse(
        UUID id,
        UUID userId,
        String villeA,
        String villeB,
        String ecole,
        String entreprise,
        LocalDate dateDebut,
        LocalDate dateFin,
        RythmeAlternance rythme,
        PremiereSemaine premiereSemaine,
        int scheduleWeeksGenerated,
        OffsetDateTime createdAt
) {
    public static AlternantProfileResponse from(AlternantProfile profile, int weeksGenerated) {
        return new AlternantProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getVilleA(),
                profile.getVilleB(),
                profile.getEcole(),
                profile.getEntreprise(),
                profile.getDateDebut(),
                profile.getDateFin(),
                profile.getRythme(),
                profile.getPremiereSemaine(),
                weeksGenerated,
                profile.getCreatedAt()
        );
    }
}
