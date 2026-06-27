package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MessageReportResponse;
import com.studup.backend.model.entity.Message;
import com.studup.backend.model.entity.MessageReport;
import com.studup.backend.model.entity.MotInterdit;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.MessageReportRepository;
import com.studup.backend.repository.MessageRepository;
import com.studup.backend.repository.MotInterditRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private MessageReportRepository reportRepository;
    @Mock private MotInterditRepository motInterditRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ModerationService service;

    private User reporter;
    private Message message;

    @BeforeEach
    void setUp() {
        reporter = User.builder()
                .id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A")
                .role(UserRole.ALTERNANT).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        message = Message.builder()
                .id(UUID.randomUUID())
                .conversationId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .content("Contenu normal")
                .isRead(false).isHidden(false)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // ─── signalement nominal → MessageReportResponse retourné ────────────────

    @Test
    void shouldHideReportedMessage() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(reporter));
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(reportRepository.existsByMessageIdAndReporterId(message.getId(), reporter.getId()))
                .thenReturn(false);

        MessageReport saved = MessageReport.builder()
                .id(UUID.randomUUID())
                .messageId(message.getId())
                .reporterId(reporter.getId())
                .motif("contenu inapproprié")
                .createdAt(OffsetDateTime.now())
                .build();
        when(reportRepository.save(any())).thenReturn(saved);

        MessageReportResponse response = service.reportMessage(
                message.getId(), "alice@studup.fr", "contenu inapproprié");

        assertThat(response.messageId()).isEqualTo(message.getId());
        assertThat(response.motif()).isEqualTo("contenu inapproprié");
        verify(reportRepository).save(any(MessageReport.class));
    }

    // ─── double signalement → IllegalStateException ───────────────────────────

    @Test
    void shouldRejectDuplicateReport() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(reporter));
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(reportRepository.existsByMessageIdAndReporterId(message.getId(), reporter.getId()))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.reportMessage(message.getId(), "alice@studup.fr", "motif"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà signalé");

        verify(reportRepository, never()).save(any());
    }

    // ─── message introuvable → ResourceNotFoundException ─────────────────────

    @Test
    void shouldThrowWhenMessageNotFoundOnReport() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(reporter));
        when(messageRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.reportMessage(UUID.randomUUID(), "alice@studup.fr", "motif"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── masquage d'un message → isHidden=true en base ───────────────────────

    @Test
    void shouldSetIsHiddenWhenHideMessage() {
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        service.hideMessage(message.getId(), "Insulte envers un autre utilisateur");

        assertThat(message.getIsHidden()).isTrue();
        assertThat(message.getModerationNote()).isEqualTo("Insulte envers un autre utilisateur");
        verify(messageRepository).save(message);
    }

    // ─── filtrage mot interdit → true si contenu match ───────────────────────

    @Test
    void shouldDetectForbiddenWord() {
        MotInterdit mot = MotInterdit.builder()
                .id(UUID.randomUUID()).mot("spam").createdAt(OffsetDateTime.now()).build();
        when(motInterditRepository.findAll()).thenReturn(List.of(mot));

        assertThat(service.containsForbiddenWord("Regarde ce SPAM incroyable")).isTrue();
    }

    // ─── filtrage mot interdit → false si contenu propre ─────────────────────

    @Test
    void shouldNotDetectForbiddenWordInCleanContent() {
        MotInterdit mot = MotInterdit.builder()
                .id(UUID.randomUUID()).mot("spam").createdAt(OffsetDateTime.now()).build();
        when(motInterditRepository.findAll()).thenReturn(List.of(mot));

        assertThat(service.containsForbiddenWord("Bonjour, je suis disponible ce week-end")).isFalse();
    }
}
