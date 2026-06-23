package com.studup.backend.controller;

import com.studup.backend.model.dto.request.SendMessageRequest;
import com.studup.backend.model.dto.response.MessageResponse;
import com.studup.backend.service.MessageService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
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
                        userDetails.getUsername(), receiverId, request.content()));
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
