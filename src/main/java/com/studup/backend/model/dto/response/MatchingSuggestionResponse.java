package com.studup.backend.model.dto.response;

import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.algorithm.Scenario;
import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.PremiereSemaine;

import java.math.BigDecimal;
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
        UUID logementBId,   // logement du candidat (destinataire)
        // Économie mensuelle estimée pour l'utilisateur connecté, en euros
        // entiers. ZERO = pas calculable (loyers inconnus) → rien à afficher.
        BigDecimal economieMensuelle,
        // Scénarios d'arrangement possibles, triés par priorité d'affichage :
        // le premier est le scénario principal de la match card (APP-109).
        List<ScenarioResponse> scenarios,
        // Rythme de l'alternant, prêt à afficher (APP-122) : « 3 sem. Paris /
        // 1 sem. Lyon ». Construit ici, à la source, pour que la logique
        // « quelle ville prend le plus de semaines » reste au même endroit que
        // la génération du calendrier — le front n'a plus qu'à l'afficher.
        String rythmeLabel,
        // Aperçu du logement de l'autre alternant (APP-122) : vignette + infos
        // sur la carte de match. null si l'autre n'a pas de logement publié.
        LogementApercuResponse logementBApercu
) {
    /**
     * isMatchActif et les IDs de logements viennent du MatchingService : le
     * CompatibilityCalculator ne voit que les calendriers, pas l'état de
     * publication des logements. Ils sont donc passés en paramètres.
     */
    public static MatchingSuggestionResponse from(AlternantProfile profile,
                                                  MatchingResult result,
                                                  boolean isMatchActif,
                                                  UUID logementAId,
                                                  UUID logementBId,
                                                  List<Scenario> scenarios,
                                                  LogementApercuResponse logementBApercu) {
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
                logementBId,
                result.economieMensuelle(),
                scenarios.stream().map(ScenarioResponse::from).toList(),
                buildRythmeLabel(profile),
                logementBApercu
        );
    }

    /**
     * Construit le libellé du rythme avec les villes : « 3 sem. Paris /
     * 1 sem. Lyon ». La ville qui « démarre » le cycle (premiereSemaine) prend
     * le premier — et le plus grand — nombre de semaines. villeA = école,
     * villeB = entreprise.
     */
    private static String buildRythmeLabel(AlternantProfile p) {
        boolean debutEntreprise = p.getPremiereSemaine() == PremiereSemaine.ENTREPRISE;
        String villeDebut = debutEntreprise ? p.getVilleB() : p.getVilleA();
        String villeAutre = debutEntreprise ? p.getVilleA() : p.getVilleB();
        return switch (p.getRythme()) {
            case SEMAINE_1_1 -> "1 sem. " + villeDebut + " / 1 sem. " + villeAutre;
            case SEMAINE_2_2 -> "2 sem. " + villeDebut + " / 2 sem. " + villeAutre;
            case SEMAINE_3_1 -> "3 sem. " + villeDebut + " / 1 sem. " + villeAutre;
            case MOIS_1_1 -> "1 mois " + villeDebut + " / 1 mois " + villeAutre;
            case AUTRE -> villeDebut + " / " + villeAutre;
        };
    }
}
