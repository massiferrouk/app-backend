package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.MessageResponse;
import com.studup.backend.model.entity.Conversation;
import com.studup.backend.model.entity.ConversationParticipant;
import com.studup.backend.model.entity.Message;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.ConversationParticipantRepository;
import com.studup.backend.repository.ConversationRepository;
import com.studup.backend.repository.MessageRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Crée ou retrouve une conversation entre deux utilisateurs, puis envoie le message
    @Transactional
    public MessageResponse sendMessage(String senderEmail, UUID receiverId, String content) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Destinataire introuvable"));

        // Retrouve la conversation existante ou en crée une nouvelle
        Conversation conversation = conversationRepository
                .findByParticipants(sender.getId(), receiverId)
                .orElseGet(() -> creerConversation(sender.getId(), receiverId));

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderId(sender.getId())
                .content(content)
                .isRead(false)
                .build();

        Message saved = messageRepository.save(message);

        // Mise à jour du timestamp de la conversation
        conversation.setLastMessageAt(OffsetDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = MessageResponse.from(saved);

        // Broadcast WebSocket : tous les abonnés au topic reçoivent le message instantanément
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversation.getId(), response);

        return response;
    }

    // Historique paginé d'une conversation (50 messages par page)
    @Transactional(readOnly = true)
    public Page<MessageResponse> getHistory(String userEmail, UUID conversationId, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        // Vérifie que l'utilisateur est bien participant à cette conversation
        participantRepository.findByConversationIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new UnauthorizedException(
                        "Vous n'êtes pas participant à cette conversation"));

        return messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(MessageResponse::from);
    }

    // Marque un message comme lu — uniquement le destinataire
    @Transactional
    public MessageResponse markAsRead(String userEmail, UUID messageId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message introuvable"));

        // Seul un participant autre que l'expéditeur peut marquer comme lu
        if (message.getSenderId().equals(user.getId())) {
            throw new UnauthorizedException("Vous ne pouvez pas marquer votre propre message comme lu");
        }

        message.setIsRead(true);
        return MessageResponse.from(messageRepository.save(message));
    }

    private Conversation creerConversation(UUID userId1, UUID userId2) {
        Conversation conversation = conversationRepository.save(
                Conversation.builder().build());

        participantRepository.save(ConversationParticipant.builder()
                .conversationId(conversation.getId()).userId(userId1).build());
        participantRepository.save(ConversationParticipant.builder()
                .conversationId(conversation.getId()).userId(userId2).build());

        return conversation;
    }
}
