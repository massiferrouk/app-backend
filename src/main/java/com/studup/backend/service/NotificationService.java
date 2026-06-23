package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.NotificationResponse;
import com.studup.backend.model.entity.Notification;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.repository.NotificationRepository;
import com.studup.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;

    public NotificationService(NotificationRepository notificationRepository,
                                UserRepository userRepository,
                                FCMService fcmService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.fcmService = fcmService;
    }

    /**
     * Méthode centrale appelée par tous les services métier (AccordService, MessageService...).
     * Persiste la notification en base ET envoie le push FCM si l'utilisateur a un token.
     */
    @Transactional
    public void notify(UUID userId, NotificationType type, String title, String body,
                       String deepLink, Map<String, String> pushData) {

        // 1. Persister la notification in-app (toujours)
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .deepLink(deepLink)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        // 2. Envoyer le push FCM si l'utilisateur a un token enregistré
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFcmToken() != null) {
                fcmService.sendNotification(user.getFcmToken(), title, body, pushData);
            }
        });
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

    // Nombre de notifications non lues (pour le badge Flutter)
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
}
