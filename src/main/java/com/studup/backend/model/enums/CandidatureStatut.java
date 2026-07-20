package com.studup.backend.model.enums;

/**
 * Étapes du suivi d'une candidature logement (APP-117).
 * L'utilisateur fait évoluer ce statut LUI-MÊME : rien n'est déduit
 * automatiquement (décision produit — on ne devine pas son intention).
 */
public enum CandidatureStatut {
    A_CONTACTER,
    CONTACTE,
    VISITE_PREVUE,
    VISITEE,
    SANS_SUITE,
    ACCEPTEE
}
