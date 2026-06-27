package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MessageReportResponse;
import com.studup.backend.model.entity.Message;
import com.studup.backend.model.entity.MessageReport;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.MessageReportRepository;
import com.studup.backend.repository.MessageRepository;
import com.studup.backend.repository.MotInterditRepository;
import com.studup.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final MessageRepository messageRepository;
    private final MessageReportRepository reportRepository;
    private final MotInterditRepository motInterditRepository;
    private final UserRepository userRepository;

    public ModerationService(MessageRepository messageRepository,
                              MessageReportRepository reportRepository,
                              MotInterditRepository motInterditRepository,
                              UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.motInterditRepository = motInterditRepository;
        this.userRepository = userRepository;
    }

    /**
     * Signale un message avec un motif.
     * Un utilisateur ne peut signaler le même message qu'une seule fois (contrainte BDD).
     */
    @Transactional
    public MessageReportResponse reportMessage(UUID messageId, String reporterEmail, String motif) {
        User reporter = userRepository.findByEmail(reporterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message introuvable : " + messageId));

        // Double signalement du même utilisateur sur le même message → erreur métier
        if (reportRepository.existsByMessageIdAndReporterId(messageId, reporter.getId())) {
            throw new IllegalStateException("Vous avez déjà signalé ce message");
        }

        MessageReport report = MessageReport.builder()
                .messageId(messageId)
                .reporterId(reporter.getId())
                .motif(motif)
                .build();

        MessageReport saved = reportRepository.save(report);
        log.info("Message signalé : messageId={} par reporterId={}", messageId, reporter.getId());

        return MessageReportResponse.from(saved);
    }

    /**
     * Retourne la queue de modération : messages signalés non encore masqués.
     * Réservé aux admins.
     */
    @Transactional(readOnly = true)
    public Page<MessageReportResponse> getPendingReports(Pageable pageable) {
        return reportRepository.findPendingReports(pageable)
                .map(MessageReportResponse::from);
    }

    /**
     * Masque un message signalé et enregistre la note de modération.
     * Le message reste en base mais isHidden=true — Flutter l'affiche comme "Message masqué".
     */
    @Transactional
    public void hideMessage(UUID messageId, String moderationNote) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message introuvable : " + messageId));

        message.setIsHidden(true);
        message.setModerationNote(moderationNote);
        messageRepository.save(message);

        log.info("Message masqué par modération : messageId={}", messageId);
    }

    /**
     * Vérifie si le contenu d'un message contient un mot interdit.
     * Appelé par MessageService avant la persistance du message.
     * Comparaison insensible à la casse.
     */
    public boolean containsForbiddenWord(String content) {
        if (content == null || content.isBlank()) return false;

        String contentLower = content.toLowerCase();
        List<String> motsInterdits = motInterditRepository.findAll()
                .stream()
                .map(m -> m.getMot().toLowerCase())
                .toList();

        return motsInterdits.stream().anyMatch(contentLower::contains);
    }
}
