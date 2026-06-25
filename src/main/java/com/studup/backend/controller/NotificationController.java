package com.studup.backend.controller;

import com.studup.backend.model.dto.request.NotificationPreferenceRequest;
import com.studup.backend.model.dto.response.NotificationPreferenceResponse;
import com.studup.backend.model.dto.response.NotificationResponse;
import com.studup.backend.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                notificationService.getMyNotifications(userDetails.getUsername(), pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> countUnread(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of("unreadCount",
                notificationService.countUnread(userDetails.getUsername())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                notificationService.markAsRead(userDetails.getUsername(), id));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of("markedAsRead",
                notificationService.markAllAsRead(userDetails.getUsername())));
    }

    @PutMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.updateFcmToken(userDetails.getUsername(), body.get("fcmToken"));
        return ResponseEntity.noContent().build();
    }

    // Récupère toutes les préférences de notification de l'utilisateur
    @GetMapping("/preferences")
    public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<NotificationPreferenceResponse> prefs = notificationService
                .getPreferences(userDetails.getUsername())
                .stream()
                .map(NotificationPreferenceResponse::from)
                .toList();
        return ResponseEntity.ok(prefs);
    }

    // Met à jour une préférence (activer/désactiver un type de notification sur un canal)
    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(
            @Valid @RequestBody NotificationPreferenceRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(NotificationPreferenceResponse.from(
                notificationService.updatePreference(
                        userDetails.getUsername(),
                        request.notificationType(),
                        request.channel(),
                        request.enabled())));
    }
}
