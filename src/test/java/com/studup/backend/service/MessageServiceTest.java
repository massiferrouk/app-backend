package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.MessageResponse;
import com.studup.backend.model.entity.Conversation;
import com.studup.backend.model.entity.ConversationParticipant;
import com.studup.backend.model.entity.Message;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.repository.ConversationParticipantRepository;
import com.studup.backend.repository.ConversationRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.MessageRepository;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private UserRepository userRepository;
    @Mock private LogementRepository logementRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ModerationService moderationService;

    @InjectMocks
    private MessageService messageService;

    private User sender;
    private User receiver;
    private Conversation conversation;
    private Message message;

    @BeforeEach
    void setUp() {
        sender = User.builder().id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        receiver = User.builder().id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("B").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        conversation = Conversation.builder()
                .id(UUID.randomUUID()).createdAt(OffsetDateTime.now()).build();

        message = Message.builder()
                .id(UUID.randomUUID()).conversationId(conversation.getId())
                .senderId(sender.getId()).content("Bonjour !")
                .isRead(false).createdAt(OffsetDateTime.now()).build();
    }

    // ─── shouldPersistMessageAndMarkAsUnread ──────────────────────────────────

    // ─── Filtrage des mots interdits (APP-121) ────────────────────────────────

    @Test
    void shouldRefuserUnMessageContenantUnMotInterdit() {
        // La liste est administrable sans redéploiement : ce contrôle est le
        // seul endroit où elle sert. Sans lui, l'écran admin ne fait rien.
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(moderationService.containsForbiddenWord(any())).thenReturn(true);

        assertThatThrownBy(() -> messageService.sendMessage(
                "alice@studup.fr", receiver.getId(), "contenu interdit"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terme interdit");

        // Rien ne doit être persisté : ni message, ni conversation
        verify(messageRepository, never()).save(any());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void shouldPersistMessageAndMarkAsUnread() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(conversationRepository.findByParticipantsSansLogement(sender.getId(), receiver.getId()))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.save(any())).thenReturn(message);
        when(conversationRepository.save(any())).thenReturn(conversation);

        MessageResponse response = messageService.sendMessage(
                "alice@studup.fr", receiver.getId(), "Bonjour !");

        assertThat(response.content()).isEqualTo("Bonjour !");
        assertThat(response.isRead()).isFalse();
        assertThat(response.senderId()).isEqualTo(sender.getId());
    }

    // ─── shouldBroadcastViaWebSocket ──────────────────────────────────────────

    @Test
    void shouldBroadcastViaWebSocket() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(conversationRepository.findByParticipantsSansLogement(any(), any()))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.save(any())).thenReturn(message);
        when(conversationRepository.save(any())).thenReturn(conversation);

        messageService.sendMessage("alice@studup.fr", receiver.getId(), "Bonjour !");

        // Vérifie que le broadcast WebSocket a bien eu lieu sur le bon topic
        verify(messagingTemplate).convertAndSend(
                eq("/topic/conversation/" + conversation.getId()), any(MessageResponse.class));
        // Et sur le topic personnel du destinataire (badge temps réel, APP-102)
        verify(messagingTemplate).convertAndSend(
                eq("/topic/user/" + receiver.getId() + "/messages"), any(MessageResponse.class));
    }

    // ─── shouldCreateNewConversationWhenNoneExists ────────────────────────────

    @Test
    void shouldCreateNewConversationWhenNoneExists() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        // Aucune conversation existante → creerConversation() est appelé
        when(conversationRepository.findByParticipantsSansLogement(any(), any()))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenReturn(conversation);
        when(participantRepository.save(any())).thenReturn(null);
        when(messageRepository.save(any())).thenReturn(message);

        MessageResponse response = messageService.sendMessage(
                "alice@studup.fr", receiver.getId(), "Premier message !");

        assertThat(response).isNotNull();
        // Vérifie que les deux participants ont bien été créés (un par utilisateur)
        verify(participantRepository, org.mockito.Mockito.times(2)).save(
                any(ConversationParticipant.class));
    }

    // ─── shouldReturnHistoryPaginated ─────────────────────────────────────────

    @Test
    void shouldReturnHistoryPaginated() {
        ConversationParticipant participation = ConversationParticipant.builder()
                .id(UUID.randomUUID()).conversationId(conversation.getId())
                .userId(sender.getId()).joinedAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), sender.getId()))
                .thenReturn(Optional.of(participation));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(
                eq(conversation.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(message)));

        Page<MessageResponse> page = messageService.getHistory(
                "alice@studup.fr", conversation.getId(), PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).content()).isEqualTo("Bonjour !");
    }

    // ─── shouldRejectHistoryForNonParticipant ─────────────────────────────────

    @Test
    void shouldRejectHistoryForNonParticipant() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(participantRepository.findByConversationIdAndUserId(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getHistory(
                "alice@studup.fr", conversation.getId(), PageRequest.of(0, 50)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─── shouldMarkMessageAsRead ──────────────────────────────────────────────

    @Test
    void shouldMarkMessageAsRead() {
        Message messageRead = Message.builder()
                .id(message.getId()).conversationId(conversation.getId())
                .senderId(sender.getId()).content("Bonjour !")
                .isRead(true).createdAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("bob@studup.fr")).thenReturn(Optional.of(receiver));
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(any())).thenReturn(messageRead);

        MessageResponse response = messageService.markAsRead("bob@studup.fr", message.getId());

        assertThat(response.isRead()).isTrue();
    }

    // ─── shouldRejectMarkAsReadBySender ───────────────────────────────────────

    @Test
    void shouldRejectMarkAsReadBySender() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        // Alice est l'expéditeur — elle ne peut pas marquer son propre message comme lu
        assertThatThrownBy(() -> messageService.markAsRead("alice@studup.fr", message.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("propre message");
    }

    // ─── shouldThrowWhenReceiverNotFound ──────────────────────────────────────

    @Test
    void shouldThrowWhenReceiverNotFound() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(
                "alice@studup.fr", UUID.randomUUID(), "Bonjour"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Destinataire");
    }

    // ─── Une conversation par annonce (APP-119) ───────────────────────────────

    @Test
    void shouldUseSeparateConversationPerLogement() {
        UUID logementBordeaux = UUID.randomUUID();
        Conversation filBordeaux = Conversation.builder()
                .id(UUID.randomUUID()).logementId(logementBordeaux)
                .createdAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(logementRepository.findById(logementBordeaux))
                .thenReturn(Optional.of(Logement.builder().id(logementBordeaux).build()));
        // Le fil de CETTE annonce existe déjà → il est réutilisé
        when(conversationRepository.findByParticipantsAndLogement(
                sender.getId(), receiver.getId(), logementBordeaux))
                .thenReturn(Optional.of(filBordeaux));
        when(messageRepository.save(any())).thenReturn(message);
        when(conversationRepository.save(any())).thenReturn(filBordeaux);

        messageService.sendMessage(
                "alice@studup.fr", receiver.getId(), "Toujours dispo ?", logementBordeaux);

        // La recherche se fait bien sur (participants + annonce), jamais sur les
        // seuls participants — sinon on retomberait sur le fil d'une autre annonce
        verify(conversationRepository).findByParticipantsAndLogement(
                sender.getId(), receiver.getId(), logementBordeaux);
        verify(conversationRepository, org.mockito.Mockito.never())
                .findByParticipantsSansLogement(any(), any());
    }

    @Test
    void shouldCreateNewConversationForSecondLogementOfSameOwner() {
        UUID logementMarseille = UUID.randomUUID();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(logementRepository.findById(logementMarseille))
                .thenReturn(Optional.of(Logement.builder().id(logementMarseille).build()));
        // Aucun fil pour cette 2e annonce, même si un fil existe pour la 1re
        when(conversationRepository.findByParticipantsAndLogement(
                any(), any(), eq(logementMarseille))).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenReturn(conversation);
        when(participantRepository.save(any())).thenReturn(null);
        when(messageRepository.save(any())).thenReturn(message);

        messageService.sendMessage(
                "alice@studup.fr", receiver.getId(), "Bonjour", logementMarseille);

        // Un NOUVEAU fil est créé, rattaché à l'annonce de Marseille.
        // save() est appelé 2 fois : création du fil, puis mise à jour du
        // lastMessageAt — c'est le PREMIER appel qui porte la création.
        org.mockito.ArgumentCaptor<Conversation> captor =
                org.mockito.ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getLogementId()).isEqualTo(logementMarseille);
    }

    @Test
    void shouldRejectMessageOnUnknownLogement() {
        UUID inconnu = UUID.randomUUID();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(logementRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(
                "alice@studup.fr", receiver.getId(), "Bonjour", inconnu))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Annonce");
    }
}
