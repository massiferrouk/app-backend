package com.studup.backend.model.dto.response;

import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.enums.AccordType;

import java.util.List;
import java.util.UUID;

public record MatchingSuggestionResponse(
        UUID profileId,
        UUID userId,
        String prenom,
        String nom,
        String villeA,
        String villeB,
        double score,
        int scorePercent,
        AccordType typePropose,
        boolean isMatchActif,
        String messageMatchPotentiel,
        int nbSemainesEchange,
        int nbSemainesColocation,
        int nbSemainesChevauchement,
        String messageResume,
        List<SemaineCompatibilite> semaines
) {
    public static MatchingSuggestionResponse from(AlternantProfile profile, MatchingResult result) {
        return new MatchingSuggestionResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getFirstName(),
                profile.getUser().getLastName(),
                profile.getVilleA(),
                profile.getVilleB(),
                result.score(),
                (int) Math.round(result.score() * 100),
                result.typePropose(),
                result.isMatchActif(),
                result.messageMatchPotentiel(),
                result.nbSemainesEchange(),
                result.nbSemainesColocation(),
                result.nbSemainesChevauchement(),
                result.messageResume(),
                result.semaines()
        );
    }
}
