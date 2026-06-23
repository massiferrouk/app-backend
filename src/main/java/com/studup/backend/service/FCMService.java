package com.studup.backend.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Envoie des notifications push via Firebase Cloud Messaging.
 * Si Firebase n'est pas initialisé (env local), le service log un warning et ne plante pas.
 */
@Service
@Slf4j
public class FCMService {

    // Envoie une notification push au token FCM d'un appareil
    public void sendNotification(String fcmToken, String title, String body, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("FCM token absent — notification push ignorée");
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase non initialisé — push notification ignorée pour title='{}'", title);
            return;
        }

        try {
            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            // Données supplémentaires pour Flutter (deep link, type, id...)
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(builder.build());
            log.info("Push FCM envoyé — messageId={}", response);

        } catch (Exception e) {
            // On ne propage pas l'exception : un échec push ne doit pas faire échouer l'action métier
            log.error("Échec envoi push FCM — token={} : {}", fcmToken.substring(0, 10) + "...", e.getMessage());
        }
    }
}
