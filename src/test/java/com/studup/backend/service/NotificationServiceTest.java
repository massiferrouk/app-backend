package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.NotificationResponse;
import com.studup.backend.model.entity.Notification;
import com.studup.backend.model.entity.NotificationPreference;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.NotificationChannel;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.NotificationPreferenceRepository;
import com.studup.backend.repository.NotificationRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private FCMService fcmService;
    @Mock private NotificationTemplateService templateService;

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
                .isRead(false).createdAt(OffsetDateTime.now()).build();
    }

    // ─── shouldRespectUserPreferences ────────────────────────────────────────

    @Test
    void shouldRespectUserPreferences() {
        // L'utilisateur a désactivé les push pour NOUVEAU_MESSAGE
        NotificationPreference pref = NotificationPreference.builder()
                .userId(user.getId()).notificationType(NotificationType.NOUVEAU_MESSAGE)
                .channel(NotificationChannel.PUSH).isEnabled(false).build();

        when(templateService.buildTemplate(eq(NotificationType.NOUVEAU_MESSAGE), any()))
                .thenReturn(new NotificationTemplateService.NotificationTemplate("Titre", "Corps"));
        when(notificationRepository.save(any())).thenReturn(notification);
        when(preferenceRepository.findByUserId(user.getId()))
                .thenReturn(List.of(pref));

        notificationService.notify(user.getId(), NotificationType.NOUVEAU_MESSAGE,
                Map.of("prenom", "Bob"), "messages/123");

        // Notification persistée en base mais push non envoyé
        verify(notificationRepository).save(any());
        verifyNoInteractions(fcmService);
    }

    // ─── shouldSendPushWhenPreferenceEnabled ─────────────────────────────────

    @Test
    void shouldSendPushWhenPreferenceEnabled() {
        when(templateService.buildTemplate(any(), any()))
                .thenReturn(new NotificationTemplateService.NotificationTemplate("Titre", "Corps"));
        when(notificationRepository.save(any())).thenReturn(notification);
        // Pas de préférence enregistrée → défaut = activé
        when(preferenceRepository.findByUserId(any()))
                .thenReturn(List.of());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(fcmService.sendNotification(any(), any(), any(), any())).thenReturn(true);

        notificationService.notify(user.getId(), NotificationType.NOUVEAU_MESSAGE,
                Map.of("prenom", "Bob"), "messages/123");

        verify(fcmService).sendNotification(eq("fcm-token-alice"), any(), any(), any());
    }

    // ─── shouldClearInvalidFcmToken ───────────────────────────────────────────

    @Test
    void shouldClearInvalidFcmToken() {
        when(templateService.buildTemplate(any(), any()))
                .thenReturn(new NotificationTemplateService.NotificationTemplate("Titre", "Corps"));
        when(notificationRepository.save(any())).thenReturn(notification);
        when(preferenceRepository.findByUserId(any()))
                .thenReturn(List.of());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        // Firebase retourne false → token invalide
        when(fcmService.sendNotification(any(), any(), any(), any())).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);

        notificationService.notify(user.getId(), NotificationType.NOUVEAU_MESSAGE,
                Map.of(), null);

        // Le token invalide doit être effacé
        verify(userRepository).save(argThat(u -> u.getFcmToken() == null));
    }

    // ─── shouldUpdatePreference ───────────────────────────────────────────────

    @Test
    void shouldUpdatePreference() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUserId(user.getId()))
                .thenReturn(List.of());
        NotificationPreference saved = NotificationPreference.builder()
                .id(UUID.randomUUID()).userId(user.getId())
                .notificationType(NotificationType.NOUVEAU_MESSAGE)
                .channel(NotificationChannel.PUSH).isEnabled(false).build();
        when(preferenceRepository.save(any())).thenReturn(saved);

        NotificationPreference result = notificationService.updatePreference(
                "alice@studup.fr", NotificationType.NOUVEAU_MESSAGE, NotificationChannel.PUSH, false);

        assertThat(result.getIsEnabled()).isFalse();
    }

    // ─── shouldReturnPagedNotifications ──────────────────────────────────────

    @Test
    void shouldReturnPagedNotifications() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(user.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(notification)));

        var page = notificationService.getMyNotifications("alice@studup.fr", PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).type()).isEqualTo(NotificationType.NOUVEAU_MESSAGE);
    }

    // ─── shouldCountUnread ────────────────────────────────────────────────────

    @Test
    void shouldCountUnread() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(user));
        when(notificationRepository.countByUserIdAndIsReadFalse(user.getId())).thenReturn(4L);

        assertThat(notificationService.countUnread("alice@studup.fr")).isEqualTo(4L);
    }

    // ─── shouldMarkAsRead ─────────────────────────────────────────────────────

    @Test
    void shouldMarkAsRead() {
        Notification readNotif = Notification.builder()
                .id(notification.getId()).userId(user.getId())
                .type(NotificationType.NOUVEAU_MESSAGE).title("T").body("B")
                .isRead(true).createdAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(user));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(readNotif);

        NotificationResponse response = notificationService.markAsRead("alice@studup.fr", notification.getId());

        assertThat(response.isRead()).isTrue();
    }

    // ─── shouldRejectMarkAsReadByWrongUser ────────────────────────────────────

    @Test
    void shouldRejectMarkAsReadByWrongUser() {
        User other = User.builder().id(UUID.randomUUID()).email("charlie@studup.fr")
                .firstName("C").lastName("C").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("charlie@studup.fr")).thenReturn(Optional.of(other));
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead("charlie@studup.fr", notification.getId()))
                .isInstanceOf(UnauthorizedException.class);
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
