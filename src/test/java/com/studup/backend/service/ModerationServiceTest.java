package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.MessageReportResponse;
import com.studup.backend.model.dto.response.MotInterditResponse;
import com.studup.backend.model.entity.Message;
import com.studup.backend.model.entity.MessageReport;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.LogementReport;
import com.studup.backend.model.entity.MotInterdit;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.MessageReportRepository;
import com.studup.backend.repository.MessageRepository;
import com.studup.backend.repository.LogementReportRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.MotInterditRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private MessageReportRepository reportRepository;
    @Mock private MotInterditRepository motInterditRepository;
    @Mock private UserRepository userRepository;
    @Mock private LogementRepository logementRepository;
    @Mock private LogementReportRepository reportLogementRepository;
    @Mock private NotificationService notificationService;

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

    // ─── File de moderation (APP-121) ─────────────────────────────────────────

    @Test
    void shouldJoindreLeContenuDuMessageAuSignalement() {
        // Sans ce contexte, l'admin ne voit que des identifiants et devrait
        // masquer un message sans pouvoir le lire.
        User auteur = User.builder()
                .id(message.getSenderId()).email("bob@studup.fr")
                .firstName("Bob").lastName("B")
                .role(UserRole.ETUDIANT).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        MessageReport report = MessageReport.builder()
                .id(UUID.randomUUID())
                .messageId(message.getId())
                .reporterId(reporter.getId())
                .motif("Propos deplaces")
                .createdAt(OffsetDateTime.now())
                .build();

        when(reportRepository.findPendingReports(any()))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(messageRepository.findAllById(any())).thenReturn(List.of(message));
        when(userRepository.findAllById(any())).thenReturn(List.of(auteur, reporter));

        Page<MessageReportResponse> page =
                service.getPendingReports(PageRequest.of(0, 20));

        MessageReportResponse vue = page.getContent().get(0);
        assertThat(vue.contenuMessage()).isEqualTo("Contenu normal");
        assertThat(vue.auteurNom()).isEqualTo("Bob B");
        assertThat(vue.signalePar()).isEqualTo("Alice A");
        assertThat(vue.motif()).isEqualTo("Propos deplaces");
    }

    @Test
    void shouldChargerMessagesEtUtilisateursEnLots() {
        // Trois signalements : on veut 2 requetes au total, pas 9.
        List<MessageReport> reports = List.of(
                reportSur(message.getId()), reportSur(message.getId()),
                reportSur(message.getId()));

        when(reportRepository.findPendingReports(any()))
                .thenReturn(new PageImpl<>(reports));
        when(messageRepository.findAllById(any())).thenReturn(List.of(message));
        when(userRepository.findAllById(any())).thenReturn(List.of(reporter));

        service.getPendingReports(PageRequest.of(0, 20));

        verify(messageRepository, times(1)).findAllById(any());
        verify(userRepository, times(1)).findAllById(any());
        verify(messageRepository, never()).findById(any());
    }

    @Test
    void shouldTolererUnMessageDisparu() {
        MessageReport report = reportSur(UUID.randomUUID());
        when(reportRepository.findPendingReports(any()))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(messageRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(reporter));

        Page<MessageReportResponse> page =
                service.getPendingReports(PageRequest.of(0, 20));

        // La file reste consultable : le signalement s'affiche sans contenu
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).contenuMessage()).isNull();
        assertThat(page.getContent().get(0).signalePar()).isEqualTo("Alice A");
    }

    // ─── Signalement d'annonces (APP-121) ─────────────────────────────────

    @Test
    void shouldSignalerUneAnnonce() {
        Logement logement = logementActif();
        User autre = User.builder()
                .id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("B")
                .role(UserRole.ETUDIANT).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        when(userRepository.findByEmail("bob@studup.fr")).thenReturn(Optional.of(autre));
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(reportLogementRepository.existsByLogementIdAndReporterId(any(), any()))
                .thenReturn(false);
        when(reportLogementRepository.save(any(LogementReport.class)))
                .thenAnswer(inv -> {
                    LogementReport r = inv.getArgument(0);
                    r.setId(UUID.randomUUID());
                    r.setCreatedAt(OffsetDateTime.now());
                    return r;
                });

        service.reportLogement(logement.getId(), "bob@studup.fr", "Annonce frauduleuse");

        verify(reportLogementRepository).save(any(LogementReport.class));
    }

    @Test
    void shouldRefuserDeSignalerSaPropreAnnonce() {
        Logement logement = logementActif(); // owner = reporter
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(reporter));
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));

        assertThatThrownBy(() -> service.reportLogement(
                logement.getId(), "alice@studup.fr", "Motif"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("votre propre annonce");

        verify(reportLogementRepository, never()).save(any());
    }

    @Test
    void shouldRefuserUnDoubleSignalement() {
        Logement logement = logementActif();
        User autre = User.builder()
                .id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("B")
                .role(UserRole.ETUDIANT).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        when(userRepository.findByEmail("bob@studup.fr")).thenReturn(Optional.of(autre));
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(reportLogementRepository.existsByLogementIdAndReporterId(any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.reportLogement(
                logement.getId(), "bob@studup.fr", "Motif"))
                .isInstanceOf(IllegalStateException.class);

        verify(reportLogementRepository, never()).save(any());
    }

    @Test
    void shouldJoindreLAnnonceEtLesNomsALaFileDeSignalements() {
        Logement logement = logementActif();
        LogementReport report = LogementReport.builder()
                .id(UUID.randomUUID())
                .logementId(logement.getId())
                .reporterId(reporter.getId())
                .motif("Photos volées")
                .createdAt(OffsetDateTime.now())
                .build();

        when(reportLogementRepository.findPendingReports(any()))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(logementRepository.findAllById(any())).thenReturn(List.of(logement));
        when(userRepository.findAllById(any())).thenReturn(List.of(reporter));

        var page = service.getPendingLogementReports(PageRequest.of(0, 20));

        var vue = page.getContent().get(0);
        assertThat(vue.logementLibelle()).contains("Paris");
        assertThat(vue.signalePar()).isEqualTo("Alice A");
        // Deux requêtes groupées, pas une lecture par ligne
        verify(logementRepository, times(1)).findAllById(any());
        verify(userRepository, times(1)).findAllById(any());
    }

    // ─── Modération des annonces (APP-121) ────────────────────────────────

    private Logement logementActif() {
        return Logement.builder()
                .id(UUID.randomUUID())
                .owner(reporter)
                .adresse("1 rue de la Paix").ville("Paris").codePostal("75001")
                .statut(LogementStatut.ACTIF)
                .isVerified(false).isMeuble(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void shouldSuspendreUneAnnonceEtPrevenirLeProprietaire() {
        Logement logement = logementActif();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(logementRepository.save(any(Logement.class))).thenAnswer(inv -> inv.getArgument(0));

        service.suspendreLogement(logement.getId(), "Photos trompeuses");

        // SUSPENDU suffit à la faire disparaître : la recherche, le matching et
        // le calendrier ne retiennent que les logements ACTIF.
        assertThat(logement.getStatut()).isEqualTo(LogementStatut.SUSPENDU);
        assertThat(logement.getModerationNote()).isEqualTo("Photos trompeuses");
        // Une annonce qui disparaît sans explication fait fuir le propriétaire
        verify(notificationService).notify(eq(reporter.getId()),
                eq(NotificationType.SYSTEME), any(), any());
    }

    @Test
    void shouldRefuserDeSuspendreDeuxFois() {
        Logement logement = logementActif();
        logement.setStatut(LogementStatut.SUSPENDU);
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));

        assertThatThrownBy(() -> service.suspendreLogement(logement.getId(), "Motif"))
                .isInstanceOf(IllegalStateException.class);

        verify(logementRepository, never()).save(any());
    }

    @Test
    void shouldRepublierEtEffacerLeMotif() {
        Logement logement = logementActif();
        logement.setStatut(LogementStatut.SUSPENDU);
        logement.setModerationNote("Photos trompeuses");
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(logementRepository.save(any(Logement.class))).thenAnswer(inv -> inv.getArgument(0));

        service.republierLogement(logement.getId());

        assertThat(logement.getStatut()).isEqualTo(LogementStatut.ACTIF);
        // La sanction est levée : la trace n'a plus à être montrée au propriétaire
        assertThat(logement.getModerationNote()).isNull();
        verify(notificationService).notify(eq(reporter.getId()),
                eq(NotificationType.SYSTEME), any(), any());
    }

    @Test
    void shouldRefuserDeRepublierUneAnnonceNonSuspendue() {
        Logement logement = logementActif();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));

        assertThatThrownBy(() -> service.republierLogement(logement.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowQuandLAnnonceAModererNExistePas() {
        UUID inconnu = UUID.randomUUID();
        when(logementRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendreLogement(inconnu, "Motif"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Mots interdits (APP-121) ─────────────────────────────────────────

    @Test
    void shouldNormaliserLeMotEnMinuscules() {
        when(motInterditRepository.existsByMotIgnoreCase(any())).thenReturn(false);
        when(motInterditRepository.save(any(MotInterdit.class)))
                .thenAnswer(inv -> {
                    MotInterdit m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    m.setCreatedAt(OffsetDateTime.now());
                    return m;
                });

        MotInterditResponse result = service.ajouterMotInterdit("  ConNard  ");

        // Le filtrage compare en minuscules : stocker la casse d'origine
        // créerait deux lignes pour un seul et même filtre.
        assertThat(result.mot()).isEqualTo("connard");
    }

    @Test
    void shouldRefuserUnMotDejaPresentMemeAvecUneAutreCasse() {
        when(motInterditRepository.existsByMotIgnoreCase("connard")).thenReturn(true);

        assertThatThrownBy(() -> service.ajouterMotInterdit("CONNARD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà dans la liste");

        verify(motInterditRepository, never()).save(any());
    }

    @Test
    void shouldListerLesMotsParOrdreAlphabetique() {
        when(motInterditRepository.findAllByOrderByMotAsc()).thenReturn(List.of(
                MotInterdit.builder().id(UUID.randomUUID()).mot("arnaque")
                        .createdAt(OffsetDateTime.now()).build(),
                MotInterdit.builder().id(UUID.randomUUID()).mot("spam")
                        .createdAt(OffsetDateTime.now()).build()));

        List<MotInterditResponse> mots = service.listerMotsInterdits();

        assertThat(mots).extracting(MotInterditResponse::mot)
                .containsExactly("arnaque", "spam");
    }

    @Test
    void shouldSupprimerUnMot() {
        MotInterdit mot = MotInterdit.builder().id(UUID.randomUUID()).mot("spam")
                .createdAt(OffsetDateTime.now()).build();
        when(motInterditRepository.findById(mot.getId())).thenReturn(Optional.of(mot));

        service.supprimerMotInterdit(mot.getId());

        verify(motInterditRepository).delete(mot);
    }

    @Test
    void shouldThrowQuandLeMotASupprimerNExistePas() {
        UUID inconnu = UUID.randomUUID();
        when(motInterditRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.supprimerMotInterdit(inconnu))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private MessageReport reportSur(UUID messageId) {
        return MessageReport.builder()
                .id(UUID.randomUUID())
                .messageId(messageId)
                .reporterId(reporter.getId())
                .motif("Motif")
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
