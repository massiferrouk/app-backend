package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MessageReportResponse;
import com.studup.backend.model.dto.response.LogementReportResponse;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.model.dto.response.MotInterditResponse;
import com.studup.backend.model.entity.Message;
import com.studup.backend.model.entity.MessageReport;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.LogementReport;
import com.studup.backend.model.entity.MotInterdit;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.MessageReportRepository;
import com.studup.backend.repository.MessageRepository;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.repository.LogementReportRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.MotInterditRepository;
import com.studup.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final MessageRepository messageRepository;
    private final MessageReportRepository reportRepository;
    private final MotInterditRepository motInterditRepository;
    private final UserRepository userRepository;
    private final LogementRepository logementRepository;
    private final LogementReportRepository reportLogementRepository;
    private final NotificationService notificationService;

    public ModerationService(MessageRepository messageRepository,
                              MessageReportRepository reportRepository,
                              MotInterditRepository motInterditRepository,
                              UserRepository userRepository,
                              LogementRepository logementRepository,
                              LogementReportRepository reportLogementRepository,
                              NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.motInterditRepository = motInterditRepository;
        this.userRepository = userRepository;
        this.logementRepository = logementRepository;
        this.reportLogementRepository = reportLogementRepository;
        this.notificationService = notificationService;
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
     *
     * Chaque signalement est enrichi du message concerné et des noms des deux
     * personnes : sans cela, le modérateur n'aurait que des identifiants et
     * devrait trancher à l'aveugle (APP-121).
     *
     * Les messages et les utilisateurs sont chargés en deux requêtes groupées.
     * Une lecture par ligne ferait, sur une page de 20 signalements, une
     * soixantaine d'allers-retours en base pour un seul écran.
     */
    @Transactional(readOnly = true)
    public Page<MessageReportResponse> getPendingReports(Pageable pageable) {
        Page<MessageReport> reports = reportRepository.findPendingReports(pageable);
        if (reports.isEmpty()) {
            return reports.map(MessageReportResponse::from);
        }

        Map<UUID, Message> messages = messageRepository
                .findAllById(reports.stream().map(MessageReport::getMessageId).toList())
                .stream()
                .collect(Collectors.toMap(Message::getId, m -> m));

        // Auteurs des messages + signaleurs, en une seule requête
        Set<UUID> userIds = new HashSet<>();
        reports.forEach(r -> userIds.add(r.getReporterId()));
        messages.values().forEach(m -> userIds.add(m.getSenderId()));

        Map<UUID, String> noms = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getFirstName() + " " + u.getLastName()));

        return reports.map(report -> {
            Message message = messages.get(report.getMessageId());
            return MessageReportResponse.withContexte(
                    report,
                    message != null ? message.getContent() : null,
                    message != null ? message.getSenderId() : null,
                    message != null ? noms.get(message.getSenderId()) : null,
                    message != null ? message.getCreatedAt() : null,
                    noms.get(report.getReporterId()));
        });
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

    // ─── Signalement d'annonces (APP-121) ─────────────────────────────────────

    /**
     * Signale une annonce. Un utilisateur ne peut le faire qu'une fois par
     * annonce — sinon la file de modération se remplirait de doublons venant
     * de la même personne.
     */
    @Transactional
    public LogementReportResponse reportLogement(UUID logementId, String reporterEmail, String motif) {
        User reporter = userRepository.findByEmail(reporterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable : " + logementId));

        if (logement.getOwner().getId().equals(reporter.getId())) {
            throw new IllegalStateException("Vous ne pouvez pas signaler votre propre annonce");
        }

        if (reportLogementRepository.existsByLogementIdAndReporterId(logementId, reporter.getId())) {
            throw new IllegalStateException("Vous avez déjà signalé cette annonce");
        }

        LogementReport saved = reportLogementRepository.save(LogementReport.builder()
                .logementId(logementId)
                .reporterId(reporter.getId())
                .motif(motif)
                .build());

        log.info("Annonce signalée : logementId={} par reporterId={}", logementId, reporter.getId());
        return LogementReportResponse.withContexte(saved, null, null, null);
    }

    /**
     * File de modération des annonces signalées.
     *
     * Même stratégie que pour les messages : deux requêtes groupées pour
     * joindre les annonces et les noms, plutôt qu'une lecture par ligne.
     */
    @Transactional(readOnly = true)
    public Page<LogementReportResponse> getPendingLogementReports(Pageable pageable) {
        Page<LogementReport> reports = reportLogementRepository.findPendingReports(pageable);
        if (reports.isEmpty()) {
            return reports.map(r -> LogementReportResponse.withContexte(r, null, null, null));
        }

        Map<UUID, Logement> logements = logementRepository
                .findAllById(reports.stream().map(LogementReport::getLogementId).toList())
                .stream()
                .collect(Collectors.toMap(Logement::getId, l -> l));

        Set<UUID> userIds = new HashSet<>();
        reports.forEach(r -> userIds.add(r.getReporterId()));
        logements.values().forEach(l -> userIds.add(l.getOwner().getId()));

        Map<UUID, String> noms = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getFirstName() + " " + u.getLastName()));

        return reports.map(report -> {
            Logement logement = logements.get(report.getLogementId());
            return LogementReportResponse.withContexte(
                    report,
                    logement != null
                            ? logement.getType() + " · " + logement.getVille()
                            : null,
                    logement != null ? noms.get(logement.getOwner().getId()) : null,
                    noms.get(report.getReporterId()));
        });
    }

    // ─── Modération des annonces (APP-121) ────────────────────────────────────

    /**
     * Liste toutes les annonces pour l'administration, filtrables par statut.
     *
     * Contrairement à la recherche publique, aucun filtre implicite : l'admin
     * doit pouvoir voir les brouillons et les annonces déjà suspendues, sans
     * quoi il ne pourrait ni vérifier son action ni la défaire.
     */
    @Transactional(readOnly = true)
    public Page<LogementResponse> listerLogements(LogementStatut statut, Pageable pageable) {
        Page<Logement> logements = statut == null
                ? logementRepository.findAll(pageable)
                : logementRepository.findByStatut(statut, pageable);

        // Pas d'URL de photo ici : la liste d'administration est une liste de
        // travail, et signer une URL par annonce coûterait un appel MinIO par
        // ligne pour une vignette dont le modérateur n'a pas besoin.
        return logements.map(l -> LogementResponse.from(l, List.of()));
    }

    /**
     * Retire une annonce de la plateforme.
     *
     * Le statut SUSPENDU existait depuis V2 sans qu'aucun code ne le pose.
     * Il est parfait ici : les logements non ACTIF sont déjà exclus de la
     * recherche, du matching et du calendrier de compatibilité. Suspendre
     * suffit donc à les faire disparaître partout, sans code supplémentaire.
     *
     * Le propriétaire est prévenu avec le motif : une annonce qui disparaît
     * sans explication est le meilleur moyen de perdre un utilisateur.
     */
    @Transactional
    public LogementResponse suspendreLogement(UUID logementId, String motif) {
        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable : " + logementId));

        if (logement.getStatut() == LogementStatut.SUSPENDU) {
            throw new IllegalStateException("Cette annonce est déjà suspendue");
        }

        logement.setStatut(LogementStatut.SUSPENDU);
        logement.setModerationNote(motif);
        Logement saved = logementRepository.save(logement);

        notificationService.notify(
                logement.getOwner().getId(),
                NotificationType.SYSTEME,
                Map.of("titre", "Ton annonce a été retirée",
                        "corps", "« " + logement.getVille() + " » n'est plus visible : " + motif),
                "logement/" + logementId);

        log.info("Annonce suspendue par modération : logementId={}", logementId);
        return LogementResponse.from(saved, List.of());
    }

    /**
     * Remet une annonce en ligne et efface le motif : la sanction est levée,
     * la trace n'a plus lieu d'être affichée au propriétaire.
     */
    @Transactional
    public LogementResponse republierLogement(UUID logementId) {
        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable : " + logementId));

        if (logement.getStatut() != LogementStatut.SUSPENDU) {
            throw new IllegalStateException("Cette annonce n'est pas suspendue");
        }

        logement.setStatut(LogementStatut.ACTIF);
        logement.setModerationNote(null);
        Logement saved = logementRepository.save(logement);

        notificationService.notify(
                logement.getOwner().getId(),
                NotificationType.SYSTEME,
                Map.of("titre", "Ton annonce est de nouveau en ligne",
                        "corps", "« " + logement.getVille() + " » est visible à nouveau."),
                "logement/" + logementId);

        log.info("Annonce republiée par modération : logementId={}", logementId);
        return LogementResponse.from(saved, List.of());
    }

    // ─── Mots interdits (APP-121) ─────────────────────────────────────────────

    /**
     * Liste les mots filtrés, par ordre alphabétique.
     *
     * La table existe depuis la migration V21, avec pour intention affichée
     * « configurables par l'admin sans redéploiement » — mais aucun endpoint
     * ne le permettait : la liste était figée à ce que contenait la base.
     */
    @Transactional(readOnly = true)
    public List<MotInterditResponse> listerMotsInterdits() {
        return motInterditRepository.findAllByOrderByMotAsc().stream()
                .map(MotInterditResponse::from)
                .toList();
    }

    /**
     * Ajoute un mot à la liste filtrée.
     *
     * Le mot est normalisé en minuscules : le filtrage compare en minuscules,
     * donc stocker « Con » et « con » créerait deux lignes pour un seul et
     * même filtre. La contrainte UNIQUE de PostgreSQL ne l'aurait pas vu,
     * elle est sensible à la casse.
     */
    @Transactional
    public MotInterditResponse ajouterMotInterdit(String mot) {
        String normalise = mot.trim().toLowerCase();

        if (motInterditRepository.existsByMotIgnoreCase(normalise)) {
            throw new IllegalStateException("Ce mot est déjà dans la liste");
        }

        MotInterdit saved = motInterditRepository.save(
                MotInterdit.builder().mot(normalise).build());

        log.info("Mot interdit ajouté : id={}", saved.getId());
        return MotInterditResponse.from(saved);
    }

    /** Retire un mot de la liste filtrée. */
    @Transactional
    public void supprimerMotInterdit(UUID id) {
        MotInterdit mot = motInterditRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mot introuvable : " + id));

        motInterditRepository.delete(mot);
        log.info("Mot interdit supprimé : id={}", id);
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
