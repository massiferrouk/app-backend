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
        List<SemaineCompatibilite> semaines,
        // Logements publiés et associés qui rendent l'échange signable.
        // null si l'alternant concerné n'a pas encore publié son logement (match potentiel).
        UUID logementAId,   // logement de l'utilisateur connecté (initiateur)
        UUID logementBId    // logement du candidat (destinataire)
) {
    /**
     * isMatchActif et les IDs de logements sont calculés dans le MatchingService
     * (le CompatibilityCalculator ne connaît pas les logements), on les passe donc
     * explicitement ici plutôt que via result.isMatchActif() (toujours false).
     */
    public static MatchingSuggestionResponse from(AlternantProfile profile,
                                                  MatchingResult result,
                                                  boolean isMatchActif,
                                                  UUID logementAId,
                                                  UUID logementBId) {
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
                isMatchActif,
                result.messageMatchPotentiel(),
                result.nbSemainesEchange(),
                result.nbSemainesColocation(),
                result.nbSemainesChevauchement(),
                result.messageResume(),
                result.semaines(),
                logementAId,
                logementBId
        );
    }
}
