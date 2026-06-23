package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.NotificationResponse;
import com.studup.backend.model.entity.Notification;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.NotificationRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private FCMService fcmService;

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .fcmToken("fcm-token-alice")
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        notification = Notification.builder()
                .id(UUID.randomUUID()).userId(user.getId())
                .type(NotificationType.NOUVEAU_MESSAGE)
                .title("Nouveau message").body("Bob vous a envoyé un message")
                .isRead(false).deepLink("messages/conv-123")
                .createdAt(OffsetDateTime.now()).build();
    }

    // ─── shouldPersistNotificationAndSendPush ────────────────────────────────

    @Test
    void shouldPersistNotificationAndSendPush() {
        when(notificationRepository.save(any())).thenReturn(notification);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        notificationService.notify(
                user.getId(), NotificationType.NOUVEAU_MESSAGE,
                "Nouveau message", "Bob vous a envoyé un message",
                "messages/conv-123", Map.of("type", "NOUVEAU_MESSAGE"));

        verify(notificationRepository).save(any(Notification.class));
        // FCM envoyé car l'utilisateur a un token
        verify(fcmService).sendNotification(eq("fcm-token-alice"), any(), any(), any());
    }

    // ─── shouldSkipPushWhenNoFcmToken ────────────────────────────────────────

    @Test
    void shouldSkipPushWhenNoFcmToken() {
        User userWithoutToken = User.builder()
                .id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("B").role(UserRole.ALTERNANT)
                .fcmToken(null) // pas de token
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        when(notificationRepository.save(any())).thenReturn(notification);
        when(userRepository.findById(userWithoutToken.getId())).thenReturn(Optional.of(userWithoutToken));

        notificationService.notify(
                userWithoutToken.getId(), NotificationType.NOUVEAU_MESSAGE,
                "Titre", "Corps", null, null);

        verify(notificationRepository).save(any());
        // FCM non appelé car pas de token
        verifyNoInteractions(fcmService);
    }

    // ─── shouldReturnPagedNotifications ──────────────────────────────────────

    @Test
    void shouldReturnPagedNotifications() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(user.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<NotificationResponse> page = notificationService.getMyNotifications(
                "alice@studup.fr", PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).type()).isEqualTo(NotificationType.NOUVEAU_MESSAGE);
    }

    // ─── shouldCountUnreadNotifications ──────────────────────────────────────

    @Test
    void shouldCountUnreadNotifications() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(user));
        when(notificationRepository.countByUserIdAndIsReadFalse(user.getId())).thenReturn(3L);

        long count = notificationService.countUnread("alice@studup.fr");

        assertThat(count).isEqualTo(3L);
    }

    // ─── shouldMarkNotificationAsRead ────────────────────────────────────────

    @Test
    void shouldMarkNotificationAsRead() {
        Notification readNotif = Notification.builder()
                .id(notification.getId()).userId(user.getId())
                .type(NotificationType.NOUVEAU_MESSAGE)
                .title("Nouveau message").body("Corps")
                .isRead(true).createdAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(user));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(readNotif);

        NotificationResponse response = notificationService.markAsRead("alice@studup.fr", notification.getId());

        assertThat(response.isRead()).isTrue();
    }

    // ─── shouldRejectMarkAsReadByOtherUser ───────────────────────────────────

    @Test
    void shouldRejectMarkAsReadByOtherUser() {
        User otherUser = User.builder()
                .id(UUID.randomUUID()).email("charlie@studup.fr")
                .firstName("Charlie").lastName("C").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("charlie@studup.fr")).thenReturn(Optional.of(otherUser));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        // La notification appartient à alice, pas à charlie
        assertThatThrownBy(() -> notificationService.markAsRead("charlie@studup.fr", notification.getId()))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─── shouldUpdateFcmToken ─────────────────────────────────────────────────

    @Test
    void shouldUpdateFcmToken() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        notificationService.updateFcmToken("alice@studup.fr", "new-fcm-token-xyz");

        verify(userRepository).save(argThat(u -> "new-fcm-token-xyz".equals(u.getFcmToken())));
    }

    // ─── shouldThrowWhenUserNotFound ──────────────────────────────────────────

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getMyNotifications(
                "inconnu@studup.fr", PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
