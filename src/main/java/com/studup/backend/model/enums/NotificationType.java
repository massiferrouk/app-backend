package com.studup.backend.model.enums;

public enum NotificationType {
    NOUVEAU_MATCH,
    DEMANDE_ACCORD,
    ACCORD_ACCEPTE,
    ACCORD_REFUSE,
    NOUVEAU_MESSAGE,
    AVIS_RECU,
    DOCUMENT_VALIDE,
    DOCUMENT_REFUSE,
    RAPPEL_DEPART,
    RAPPEL_ARRIVEE,
    // Un étudiant a mis l'annonce en favori (APP-119) — destiné au propriétaire.
    // On signale l'intérêt, jamais le statut de suivi, qui reste privé.
    ANNONCE_SUIVIE,
    SYSTEME
}
