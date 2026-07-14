package com.studup.backend.service;

import com.studup.backend.model.enums.NotificationType;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Centralise tous les templates de notifications.
 * Chaque type a un titre et un corps distincts, paramétrables via les données contextuelles.
 */
@Service
public class NotificationTemplateService {

    public record NotificationTemplate(String title, String body) {}

    public NotificationTemplate buildTemplate(NotificationType type, Map<String, String> data) {
        String prenom = data.getOrDefault("prenom", "quelqu'un");

        return switch (type) {
            case NOUVEAU_MESSAGE -> new NotificationTemplate(
                    "Nouveau message",
                    prenom + " vous a envoyé un message"
            );
            case DEMANDE_ACCORD -> new NotificationTemplate(
                    "Nouvelle demande d'accord",
                    prenom + " souhaite échanger son logement avec vous"
            );
            case ACCORD_ACCEPTE -> new NotificationTemplate(
                    "Accord accepté !",
                    prenom + " a accepté votre demande d'échange"
            );
            case ACCORD_REFUSE -> new NotificationTemplate(
                    "Accord refusé",
                    prenom + " n'a pas pu accepter votre demande"
            );
            case AVIS_RECU -> new NotificationTemplate(
                    "Nouvel avis",
                    prenom + " vous a laissé un avis"
            );
            case DOCUMENT_VALIDE -> new NotificationTemplate(
                    "Document validé",
                    "Votre " + data.getOrDefault("typeDocument", "document") + " a été validé"
            );
            case DOCUMENT_REFUSE -> new NotificationTemplate(
                    "Document refusé",
                    "Votre " + data.getOrDefault("typeDocument", "document") + " a été refusé. " +
                    data.getOrDefault("raison", "Veuillez soumettre un nouveau document.")
            );
            case RAPPEL_DEPART -> new NotificationTemplate(
                    "Rappel départ",
                    "Votre échange commence " + data.getOrDefault("date", "bientôt") + ". Bon voyage !"
            );
            case RAPPEL_ARRIVEE -> new NotificationTemplate(
                    "Rappel arrivée",
                    prenom + " arrive " + data.getOrDefault("date", "bientôt") + " dans votre logement"
            );
            case NOUVEAU_MATCH -> new NotificationTemplate(
                    "Nouveau match !",
                    prenom + " a un profil compatible avec le vôtre — découvrez votre échange possible"
            );
            case SYSTEME -> new NotificationTemplate(
                    data.getOrDefault("titre", "Information"),
                    data.getOrDefault("corps", "Mise à jour de la plateforme StudUp")
            );
        };
    }
}
