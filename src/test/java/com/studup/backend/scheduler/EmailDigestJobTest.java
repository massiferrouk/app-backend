package com.studup.backend.scheduler;

import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.MessageRepository;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDigestJobTest {

    @Mock private UserRepository userRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private AccordRepository accordRepository;
    @Mock private EmailService emailService;

    @InjectMocks private EmailDigestJob job;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        job = new EmailDigestJob(userRepository, messageRepository,
                accordRepository, emailService, "http://localhost:8080");

        alice = User.builder()
                .id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        bob = User.builder()
                .id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("B").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    // ─── utilisateur avec messages non lus → reçoit le digest ────────────────

    @Test
    void shouldSendDigestToUsersWithUnreadMessages() {
        when(userRepository.findByIsActiveTrueAndDeletedAtIsNull()).thenReturn(List.of(alice));
        when(messageRepository.countUnreadForUser(alice.getId())).thenReturn(3L);
        when(accordRepository.findAccordsEnAttenteForReceiver(alice.getId())).thenReturn(List.of());
        when(accordRepository.findProchainAccords(eq(alice.getId()), any())).thenReturn(List.of());

        job.sendWeeklyDigest();

        verify(emailService).sendHtml(
                eq("alice@studup.fr"),
                anyString(),
                eq("email-digest"),
                any());
    }

    // ─── utilisateur sans activité → ne reçoit pas le digest ─────────────────

    @Test
    void shouldNotSendDigestToInactiveUsers() {
        when(userRepository.findByIsActiveTrueAndDeletedAtIsNull()).thenReturn(List.of(alice));
        when(messageRepository.countUnreadForUser(alice.getId())).thenReturn(0L);
        when(accordRepository.findAccordsEnAttenteForReceiver(alice.getId())).thenReturn(List.of());
        when(accordRepository.findProchainAccords(eq(alice.getId()), any())).thenReturn(List.of());

        job.sendWeeklyDigest();

        verify(emailService, never()).sendHtml(any(), any(), any(), any());
    }

    // ─── variables du template correctement renseignées ──────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void shouldPassCorrectVariablesToTemplate() {
        Accord enAttente = Accord.builder()
                .id(UUID.randomUUID())
                .initiatorId(UUID.randomUUID()).receiverId(alice.getId())
                .type(AccordType.ECHANGE_TOTAL).statut(AccordStatut.EN_ATTENTE)
                .createdAt(OffsetDateTime.now()).build();

        when(userRepository.findByIsActiveTrueAndDeletedAtIsNull()).thenReturn(List.of(alice));
        when(messageRepository.countUnreadForUser(alice.getId())).thenReturn(2L);
        when(accordRepository.findAccordsEnAttenteForReceiver(alice.getId())).thenReturn(List.of(enAttente));
        when(accordRepository.findProchainAccords(eq(alice.getId()), any())).thenReturn(List.of());

        job.sendWeeklyDigest();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendHtml(any(), any(), any(), captor.capture());

        Map<String, Object> vars = captor.getValue();
        assertThat(vars.get("prenom")).isEqualTo("Alice");
        assertThat(vars.get("nbMessagesNonLus")).isEqualTo(2L);
        assertThat(vars.get("nbAccordsEnAttente")).isEqualTo(1L);
        assertThat(vars.get("unsubscribeUrl").toString()).contains("/notifications/unsubscribe/");
    }

    // ─── plusieurs utilisateurs : seuls ceux avec activité reçoivent ─────────

    @Test
    void shouldOnlySendToActiveUsers() {
        when(userRepository.findByIsActiveTrueAndDeletedAtIsNull()).thenReturn(List.of(alice, bob));

        // Alice : 1 message non lu
        when(messageRepository.countUnreadForUser(alice.getId())).thenReturn(1L);
        when(accordRepository.findAccordsEnAttenteForReceiver(alice.getId())).thenReturn(List.of());
        when(accordRepository.findProchainAccords(eq(alice.getId()), any())).thenReturn(List.of());

        // Bob : aucune activité
        when(messageRepository.countUnreadForUser(bob.getId())).thenReturn(0L);
        when(accordRepository.findAccordsEnAttenteForReceiver(bob.getId())).thenReturn(List.of());
        when(accordRepository.findProchainAccords(eq(bob.getId()), any())).thenReturn(List.of());

        job.sendWeeklyDigest();

        verify(emailService, times(1)).sendHtml(eq("alice@studup.fr"), any(), any(), any());
        verify(emailService, never()).sendHtml(eq("bob@studup.fr"), any(), any(), any());
    }
}
