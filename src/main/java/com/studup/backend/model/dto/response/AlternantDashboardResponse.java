package com.studup.backend.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * KPIs de l'accueil alternant.
 *
 * APP-120 : « économies réalisées » et « échanges terminés » supprimés — ils
 * comptaient les accords au statut TERMINE, jamais atteint par le code, et la
 * décision « messagerie-first » rend les accords formels rares de toute façon :
 * ils affichaient 0 à vie. Remplacés par des chiffres vivants, tirés du
 * matching : le nombre de matches et la meilleure économie POSSIBLE.
 */
public record AlternantDashboardResponse(
        List<AccordSummaryResponse> prochainAccords,
        List<AccordSummaryResponse> accordsEnAttente,
        /** Meilleure économie POSSIBLE parmi les matches — un potentiel, pas un acquis */
        BigDecimal economiePossibleMax,
        int nbMatchesCompatibles
) {}
