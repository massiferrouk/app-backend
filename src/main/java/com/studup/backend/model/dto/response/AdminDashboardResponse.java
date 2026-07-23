package com.studup.backend.model.dto.response;

import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.UserRole;

import java.util.Map;

/**
 * Tableau de bord de l'administration (APP-121).
 *
 * Ne contient que des chiffres réellement calculables à partir des données
 * existantes. Aucune métrique de revenus (les paiements sont hors périmètre),
 * aucune métrique d'accords ni d'avis (retirés de l'application en APP-120) :
 * elles afficheraient un zéro permanent, ce qui est pire que ne rien montrer.
 */
public record AdminDashboardResponse(
        long totalComptes,
        Map<UserRole, Long> comptesParRole,
        long comptesSuspendus,
        long comptesBannis,
        long inscriptions7Jours,
        long inscriptions30Jours,

        long totalAnnonces,
        Map<LogementStatut, Long> annoncesParStatut,

        /** Messages signalés en attente de décision */
        long signalementsEnAttente,
        /** Annonces signalées encore en ligne (APP-121) */
        long annoncesSignalees,
        long motsInterdits
) {}
