package com.studup.backend.model.enums;

/**
 * Ordre de départ du cycle d'alternance (APP-110).
 * Détermine si la première semaine du calendrier généré est une semaine
 * école (label 'A') ou entreprise (label 'B').
 */
public enum PremiereSemaine {
    ECOLE,       // le cycle commence par la période école
    ENTREPRISE   // le cycle commence par la période entreprise
}
