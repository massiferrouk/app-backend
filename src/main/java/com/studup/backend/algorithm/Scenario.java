package com.studup.backend.algorithm;

import java.math.BigDecimal;

/**
 * Un arrangement possible entre deux alternants, avec le message affiché
 * à l'utilisateur et l'action qu'il peut faire (APP-109).
 *
 * L'app ne décide pas : elle liste les scénarios, l'utilisateur choisit.
 */
public record Scenario(
        ScenarioType type,
        String message,
        // Économie mensuelle pour l'utilisateur connecté, en euros entiers.
        // ZERO = non calculable avec les loyers connus → rien d'affiché.
        BigDecimal economieMensuelle,
        ScenarioAction action
) {

    /** Types de scénarios, du document de règles APP-109 */
    public enum ScenarioType {
        TON_LOGEMENT_MANQUE,   // S2 : B a publié, pas toi
        SON_LOGEMENT_MANQUE,   // S3 : toi publié, pas B
        AUCUN_LOGEMENT,        // S4 : personne n'a publié
        REEQUILIBRER,          // S6a : lâcher un logement V1, en trouver un V2
        COLOC_UNE_VILLE,       // S6b : partager V1, chacun le sien à V2
        RELAIS                 // S6c : un seul logement V1, jamais ensemble
    }

    /** Ce que l'utilisateur peut faire depuis le scénario */
    public enum ScenarioAction {
        PUBLIER_LOGEMENT,
        CONTACTER,
        AUCUNE
    }
}
