package com.studup.backend.controller;

import com.studup.backend.model.dto.request.ReportMessageRequest;
import com.studup.backend.model.dto.request.SendMessageRequest;
import com.studup.backend.model.dto.response.MessagePhotoResponse;
import com.studup.backend.model.dto.response.MessageReportResponse;
import com.studup.backend.model.dto.response.ConversationSummaryResponse;
import com.studup.backend.model.dto.response.MessageResponse;
import com.studup.backend.service.MediaMessageService;
import com.studup.backend.service.MessageService;
import com.studup.backend.service.ModerationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;
    private final MediaMessageService mediaMessageService;
    private final ModerationService moderationService;

    public MessageController(MessageService messageService,
                              MediaMessageService mediaMessageService,
                              ModerationService moderationService) {
        this.messageService = messageService;
        this.mediaMessageService = mediaMessageService;
        this.moderationService = moderationService;
    }

    // Envoi d'un message via HTTP (REST)
    // Le message est aussi broadcasté via WebSocket dans le service
    @PostMapping("/send/{receiverId}")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID receiverId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.sendMessage(
                        userDetails.getUsername(), receiverId,
                        request.content(), request.logementId()));
    }

    // Liste des conversations de l'utilisateur connecté (APP-75)
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummaryResponse>> getMesConversations(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                messageService.getMesConversations(userDetails.getUsername()));
    }

    // Historique paginé d'une conversation (50 messages par page, plus récents en premier)
    @GetMapping("/{conversationId}")
    public ResponseEntity<Page<MessageResponse>> getHistory(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
                messageService.getHistory(userDetails.getUsername(), conversationId, pageable));
    }

    // Marquer un message comme lu
    @PatchMapping("/{messageId}/read")
    public ResponseEntity<MessageResponse> markAsRead(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                messageService.markAsRead(userDetails.getUsername(), messageId));
    }

    // Upload de photos dans un message (max 5, max 5Mo chacune, JPEG/PNG/WEBP)
    // Flutter envoie un multipart/form-data avec le champ "photos"
    @PostMapping("/{messageId}/photos")
    public ResponseEntity<MessagePhotoResponse> uploadPhotos(
            @PathVariable UUID messageId,
            @RequestPart("photos") List<MultipartFile> photos) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaMessageService.uploadPhotos(messageId, photos));
    }

    // Signalement d'un message par un utilisateur — transmis à la queue de modération admin
    @PostMapping("/{messageId}/report")
    public ResponseEntity<MessageReportResponse> reportMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody ReportMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(moderationService.reportMessage(
                        messageId, userDetails.getUsername(), request.motif()));
    }

    // Endpoint WebSocket STOMP : Flutter envoie vers /app/chat/{conversationId}
    // Le service broadcast ensuite vers /topic/conversation/{conversationId}
    @MessageMapping("/chat/{conversationId}")
    public void handleWebSocketMessage(
            @DestinationVariable UUID conversationId,
            @Payload SendMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        // Le broadcast est géré dans sendMessage via SimpMessagingTemplate
        // On passe par l'endpoint HTTP pour la persistance et le broadcast unifié
    }
}
