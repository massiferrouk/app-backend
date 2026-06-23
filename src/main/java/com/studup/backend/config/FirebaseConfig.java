package com.studup.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Initialise Firebase Admin SDK au démarrage de l'application.
 * Le fichier de credentials est passé en base64 via variable d'environnement
 * (jamais commité dans le repo).
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.service-account-base64:}")
    private String serviceAccountBase64;

    @PostConstruct
    public void initialize() {
        // Si la variable n'est pas configurée (env local sans Firebase), on skip silencieusement
        if (!StringUtils.hasText(serviceAccountBase64)) {
            log.warn("Firebase non configuré — FIREBASE_SERVICE_ACCOUNT_BASE64 absent. Les push notifications sont désactivées.");
            return;
        }

        // Évite de ré-initialiser si déjà fait (important en tests)
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64);
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(decoded));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialisé avec succès");
        } catch (Exception e) {
            log.error("Échec initialisation Firebase : {}", e.getMessage());
        }
    }
}
