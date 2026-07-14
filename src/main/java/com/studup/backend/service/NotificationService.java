package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.NotificationResponse;
import com.studup.backend.model.entity.Notification;
import com.studup.backend.model.entity.NotificationPreference;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.NotificationChannel;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.repository.NotificationPreferenceRepository;
import com.studup.backend.repository.NotificationRepository;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.service.NotificationTemplateService.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;
    private final NotificationTemplateService templateService;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationPreferenceRepository preferenceRepository,
                                UserRepository userRepository,
                                FCMService fcmService,
                                NotificationTemplateService templateService) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.fcmService = fcmService;
        this.templateService = templateService;
    }

    /**
     * Méthode centrale appelée par tous les services métier.
     * 1. Construit le template selon le type
     * 2. Persiste la notification in-app (toujours)
     * 3. Vérifie les préférences de l'utilisateur
     * 4. Envoie le push FCM si autorisé
     * 5. Nettoie le token si invalide
     */
    @Transactional
    public void notify(UUID userId, NotificationType type, Map<String, String> contextData, String deepLink) {
        // 1. Construire le template
        NotificationTemplate template = templateService.buildTemplate(type, contextData != null ? contextData : Map.of());

        // 2. Persister la notification in-app (toujours, indépendamment des préférences push)
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(template.title())
                .body(template.body())
                .deepLink(deepLink)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        // 3. Vérifier la préférence push de l'utilisateur
        boolean pushAutorise = isPushEnabled(userId, type);
        if (!pushAutorise) {
            log.debug("Push désactivé pour userId={} type={}", userId, type);
            return;
        }

        // 4. Envoyer le push FCM si l'utilisateur a un token
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFcmToken() != null) {
                boolean success = fcmService.sendNotification(
                        user.getFcmToken(), template.title(), template.body(),
                        Map.of("type", type.name(), "deepLink", deepLink != null ? deepLink : ""));

                // 5. Nettoyer le token invalide
                if (!success) {
                    log.warn("Token FCM invalide pour userId={} — suppression", userId);
                    user.setFcmToken(null);
                    userRepository.save(user);
                }
            }
        });
    }

    // Vérifie si l'utilisateur a activé les push pour ce type (défaut : activé)
    private boolean isPushEnabled(UUID userId, NotificationType type) {
        // Si aucune préférence enregistrée → on envoie (opt-out par défaut, pas opt-in)
        return findPreference(userId, type, NotificationChannel.PUSH)
                .map(NotificationPreference::getIsEnabled).orElse(true);
    }

    /**
     * Retrouve une préférence par (type, canal) en filtrant en mémoire.
     *
     * On NE fait PAS de requête dérivée du type
     * findByUserIdAndNotificationTypeAndChannel : les colonnes notification_type
     * et channel sont des ENUM PostgreSQL natifs avec @ColumnTransformer en
     * écriture seule. Une comparaison SQL directe (WHERE notification_type = ?)
     * provoque l'erreur 42883 « operator does not exist » (cf. APP-91).
     * On charge donc toutes les préférences de l'utilisateur (filtre sur user_id
     * uniquement) et on filtre côté Java.
     */
    private Optional<NotificationPreference> findPreference(
            UUID userId, NotificationType type, NotificationChannel channel) {
        return preferenceRepository.findByUserId(userId).stream()
                .filter(p -> p.getNotificationType() == type && p.getChannel() == channel)
                .findFirst();
    }

    // Récupère toutes les préférences d'un utilisateur
    @Transactional(readOnly = true)
    public List<NotificationPreference> getPreferences(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return preferenceRepository.findByUserId(user.getId());
    }

    // Met à jour ou crée une préférence
    @Transactional
    public NotificationPreference updatePreference(String userEmail, NotificationType type,
                                                    NotificationChannel channel, boolean enabled) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        NotificationPreference pref = findPreference(user.getId(), type, channel)
                .orElse(NotificationPreference.builder()
                        .userId(user.getId())
                        .notificationType(type)
                        .channel(channel)
                        .build());

        pref.setIsEnabled(enabled);
        return preferenceRepository.save(pref);
    }

    // Met à jour le token FCM après chaque login Flutter
    @Transactional
    public void updateFcmToken(String userEmail, String fcmToken) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        user.setFcmToken(fcmToken);
        userRepository.save(user);
        log.info("FCM token mis à jour pour userId={}", user.getId());
    }

    // Liste paginée des notifications de l'utilisateur connecté
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(NotificationResponse::from);
    }

    // Nombre de notifications non lues (badge Flutter)
    @Transactional(readOnly = true)
    public long countUnread(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    // Marquer une notification comme lue
    @Transactional
    public NotificationResponse markAsRead(String userEmail, UUID notificationId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification introuvable"));
        if (!notification.getUserId().equals(user.getId())) {
            throw new UnauthorizedException("Cette notification ne vous appartient pas");
        }
        notification.setIsRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    // Marquer toutes les notifications comme lues
    @Transactional
    public int markAllAsRead(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return notificationRepository.markAllAsRead(user.getId());
    }

    // Désactive le digest hebdomadaire par email — appelé depuis le lien de désabonnement
    @Transactional
    public void disableEmailDigest(UUID userId) {
        NotificationPreference pref = findPreference(
                userId, NotificationType.SYSTEME, NotificationChannel.EMAIL)
                .orElse(NotificationPreference.builder()
                        .userId(userId)
                        .notificationType(NotificationType.SYSTEME)
                        .channel(NotificationChannel.EMAIL)
                        .build());
        pref.setIsEnabled(false);
        preferenceRepository.save(pref);
        log.info("Désabonnement digest email pour userId={}", userId);
    }
}
