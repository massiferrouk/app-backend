package com.studup.backend.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Envoie des notifications push via Firebase Cloud Messaging.
 * Retourne true si l'envoi a réussi, false si le token est invalide (à nettoyer).
 * Ne propage jamais d'exception — un échec push ne doit pas bloquer l'action métier.
 */
@Service
@Slf4j
public class FCMService {

    /**
     * Envoie une notification push.
     * Retourne false si le token FCM est mort (UNREGISTERED ou INVALID_ARGUMENT)
     * afin que l'appelant puisse le supprimer de la base.
     */
    public boolean sendNotification(String fcmToken, String title, String body, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("FCM token absent — notification push ignorée");
            return false;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase non initialisé — push notification ignorée pour title='{}'", title);
            return false;
        }

        try {
            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(builder.build());
            log.info("Push FCM envoyé — messageId={}", response);
            return true;

        } catch (FirebaseMessagingException e) {
            // Token invalide ou désinstallation de l'app → signaler à l'appelant pour nettoyage
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Token FCM invalide détecté — nettoyage nécessaire : {}", e.getMessage());
                return false;
            }
            log.error("Échec envoi push FCM : {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Erreur inattendue FCM : {}", e.getMessage());
            return false;
        }
    }
}
