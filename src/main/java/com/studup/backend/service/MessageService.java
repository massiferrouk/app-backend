package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.ConversationSummaryResponse;
import com.studup.backend.model.dto.response.MessageResponse;
import com.studup.backend.model.entity.Conversation;
import com.studup.backend.model.entity.ConversationParticipant;
import com.studup.backend.model.entity.Message;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.repository.ConversationParticipantRepository;
import com.studup.backend.repository.ConversationRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.MessageRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final LogementRepository logementRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserRepository userRepository,
                          LogementRepository logementRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.logementRepository = logementRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Crée ou retrouve une conversation entre deux utilisateurs, puis envoie le message
    @Transactional
    public MessageResponse sendMessage(String senderEmail, UUID receiverId, String content) {
        return sendMessage(senderEmail, receiverId, content, null);
    }

    /**
     * Envoie un message, dans le fil de l'annonce [logementId] si elle est
     * fournie (APP-119).
     *
     * Une conversation est identifiée par (participants + annonce) et non par
     * les seuls participants : un propriétaire qui publie plusieurs logements
     * doit avoir un fil par annonce, sinon on ne sait plus de quel bien on parle.
     * [logementId] null = discussion de personne à personne (match alternant).
     */
    @Transactional
    public MessageResponse sendMessage(String senderEmail, UUID receiverId,
                                       String content, UUID logementId) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Destinataire introuvable"));

        // L'annonce doit exister — sinon on créerait un fil orphelin
        if (logementId != null) {
            logementRepository.findById(logementId)
                    .orElseThrow(() -> new ResourceNotFoundException("Annonce introuvable"));
        }

        // Retrouve la conversation de CETTE annonce, ou en crée une nouvelle
        Conversation conversation = (logementId == null
                ? conversationRepository.findByParticipantsSansLogement(sender.getId(), receiverId)
                : conversationRepository.findByParticipantsAndLogement(
                        sender.getId(), receiverId, logementId))
                .orElseGet(() -> creerConversation(sender.getId(), receiverId, logementId));

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

        // Topic PERSONNEL du destinataire (APP-102) : permet au badge Messages
        // de se mettre à jour en temps réel même si son écran de chat est fermé
        // (il n'est abonné au topic de la conversation que dans le chat).
        messagingTemplate.convertAndSend(
                "/topic/user/" + receiverId + "/messages", response);

        return response;
    }

    /**
     * Liste des conversations de l'utilisateur connecté, triées par
     * activité récente, avec aperçu du dernier message et compteur
     * de non-lus (APP-75).
     */
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> getMesConversations(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        return participantRepository.findByUserId(user.getId()).stream()
                .map(cp -> toSummary(cp.getConversationId(), user.getId()))
                .sorted(Comparator.comparing(
                        ConversationSummaryResponse::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private ConversationSummaryResponse toSummary(UUID conversationId, UUID myUserId) {
        // L'autre participant (conversations à 2 uniquement dans cette version)
        User partner = participantRepository.findByConversationId(conversationId).stream()
                .filter(p -> !p.getUserId().equals(myUserId))
                .findFirst()
                .flatMap(p -> userRepository.findById(p.getUserId()))
                .orElse(null);

        // Nom abrégé — pas de nom complet dans les listes
        String partnerName = partner == null
                ? "Utilisateur"
                : partner.getFirstName() + " " + partner.getLastName().charAt(0) + ".";

        Message lastMessage = messageRepository
                .findTopByConversationIdOrderByCreatedAtDesc(conversationId)
                .orElse(null);

        long unread = messageRepository
                .countByConversationIdAndSenderIdNotAndIsReadFalse(conversationId, myUserId);

        // Annonce concernée (APP-119) — null pour une discussion de personne à
        // personne. Permet de distinguer deux fils avec le même propriétaire.
        Logement logement = conversationRepository.findById(conversationId)
                .map(Conversation::getLogementId)
                .flatMap(logementRepository::findById)
                .orElse(null);

        return new ConversationSummaryResponse(
                conversationId,
                partner == null ? null : partner.getId(),
                partnerName,
                lastMessage == null ? "" : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                unread,
                logement == null ? null : logement.getId(),
                logement == null ? null : logement.getVille(),
                logement == null || logement.getType() == null
                        ? null : logement.getType().name());
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

    private Conversation creerConversation(UUID userId1, UUID userId2, UUID logementId) {
        Conversation conversation = conversationRepository.save(
                Conversation.builder().logementId(logementId).build());

        participantRepository.save(ConversationParticipant.builder()
                .conversationId(conversation.getId()).userId(userId1).build());
        participantRepository.save(ConversationParticipant.builder()
                .conversationId(conversation.getId()).userId(userId2).build());

        return conversation;
    }
}
