package com.studup.backend.model.dto.response;

import java.util.List;

/**
 * KPIs de l'accueil propriétaire.
 *
 * APP-119 : « taux d'occupation » et « locataires actifs » supprimés — ils
 * dépendaient d'accords EN_COURS, statut qu'aucun code n'atteint jamais :
 * ils affichaient 0 pour toujours. Remplacés par des métriques qui bougent
 * réellement avec l'usage de l'app (messagerie-first) : les étudiants qui
 * suivent les annonces et les conversations ouvertes.
 */
public record ProprietaireDashboardResponse(
        int nbLogementsTotaux,
        int nbLogementsActifs,
        int nbEtudiantsInteresses,
        int nbConversations,
        List<LogementSummaryResponse> logements
) {}
