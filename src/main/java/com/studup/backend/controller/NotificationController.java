package com.studup.backend.controller;

import com.studup.backend.model.dto.response.NotificationResponse;
import com.studup.backend.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Liste paginée des notifications (20/page, plus récentes en premier)
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                notificationService.getMyNotifications(userDetails.getUsername(), pageable));
    }

    // Nombre de notifications non lues — utilisé pour le badge Flutter
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> countUnread(
            @AuthenticationPrincipal UserDetails userDetails) {
        long count = notificationService.countUnread(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // Marquer une notification spécifique comme lue
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                notificationService.markAsRead(userDetails.getUsername(), id));
    }

    // Marquer toutes les notifications comme lues (ex: ouverture du centre de notifs)
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        int count = notificationService.markAllAsRead(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("markedAsRead", count));
    }

    // Mise à jour du token FCM après chaque login Flutter
    @PutMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.updateFcmToken(userDetails.getUsername(), body.get("fcmToken"));
        return ResponseEntity.noContent().build();
    }
}
