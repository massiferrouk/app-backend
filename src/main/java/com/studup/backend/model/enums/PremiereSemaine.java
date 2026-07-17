package com.studup.backend.model.enums;

/**
 * Ordre de départ du cycle d'alternance (APP-110).
 * Détermine si la première semaine du calendrier généré est une semaine
 * école (label 'A') ou entreprise (label 'B').
 */
public enum PremiereSemaine {
    ECOLE,       // le cycle commence par la période école
    ENTREPRISE;  // le cycle commence par la période entreprise

    /**
     * Défaut appliqué quand le client n'envoie pas le champ (anciens clients) :
     * reproduit l'ordre historiquement codé en dur dans le générateur,
     * identique au backfill de la migration V24.
     */
    public static PremiereSemaine defaultFor(RythmeAlternance rythme) {
        return rythme == RythmeAlternance.SEMAINE_3_1 ? ENTREPRISE : ECOLE;
    }
}
