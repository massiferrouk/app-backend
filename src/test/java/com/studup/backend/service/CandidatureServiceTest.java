package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.CandidatureResponse;
import com.studup.backend.model.entity.Candidature;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.CandidatureStatut;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.repository.CandidatureRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.PhotoLogementRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidatureServiceTest {

    private static final String EMAIL = "massi@studup.fr";

    @Mock private CandidatureRepository candidatureRepository;
    @Mock private LogementRepository logementRepository;
    @Mock private UserRepository userRepository;
    @Mock private PhotoLogementRepository photoRepository;
    @Mock private MinioService minioService;
    @Mock private NotificationService notificationService;

    @InjectMocks private CandidatureService service;

    private User etudiant;
    private Logement logement;

    @BeforeEach
    void setUp() {
        etudiant = User.builder().id(UUID.randomUUID()).email(EMAIL)
                .firstName("Massi").lastName("F").build();
        // LogementResponse.from lit owner.getId() et owner.getFirstName()
        User proprio = User.builder().id(UUID.randomUUID()).email("proprio@studup.fr")
                .firstName("Rabah").lastName("Ammour").build();
        logement = Logement.builder().id(UUID.randomUUID()).owner(proprio)
                .ville("Paris").build();
    }

    private void stubUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(etudiant));
    }

    private void stubNoPhoto() {
        when(photoRepository.findFileKeysByLogementId(any())).thenReturn(List.of());
    }

    private Candidature candidatureExistante(CandidatureStatut statut, User owner) {
        return Candidature.builder()
                .id(UUID.randomUUID()).user(owner).logement(logement).statut(statut)
                .build();
    }

    // ─── Suivre une annonce ───────────────────────────────────────────────────

    @Test
    void shouldCreateCandidatureWhenNotFollowedYet() {
        stubUser();
        stubNoPhoto();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(candidatureRepository.findByUserIdAndLogementId(etudiant.getId(), logement.getId()))
                .thenReturn(Optional.empty());
        when(candidatureRepository.save(any(Candidature.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CandidatureResponse res = service.suivre(EMAIL, logement.getId(), null);

        // Statut par défaut quand le client n'en envoie pas
        assertThat(res.statut()).isEqualTo(CandidatureStatut.A_CONTACTER);
        assertThat(res.logement().ville()).isEqualTo("Paris");
    }

    // ─── Notification « annonce suivie » au propriétaire (APP-119) ────────────

    @Test
    void shouldNotifyOwnerWhenListingIsFollowed() {
        stubUser();
        stubNoPhoto();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(candidatureRepository.findByUserIdAndLogementId(any(), any()))
                .thenReturn(Optional.empty());
        when(candidatureRepository.save(any(Candidature.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.suivre(EMAIL, logement.getId(), null);

        // Le propriétaire est prévenu, et le contexte ne porte QUE la ville :
        // le statut de suivi reste le tableau de bord privé de l'étudiant.
        // Le payload embarque le couple (étudiant, annonce) pour dédupliquer.
        verify(notificationService).notify(
                eq(logement.getOwner().getId()),
                eq(NotificationType.ANNONCE_SUIVIE),
                eq(Map.of("ville", "Paris")),
                anyString(),
                contains(etudiant.getId().toString()));
    }

    @Test
    void shouldNotRenotifyWhenSameStudentRefollows() {
        stubUser();
        stubNoPhoto();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(candidatureRepository.findByUserIdAndLogementId(any(), any()))
                .thenReturn(Optional.empty());
        when(candidatureRepository.save(any(Candidature.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // L'étudiant avait déjà suivi cette annonce (puis retirée) :
        // une alerte existe déjà pour ce couple (étudiant, annonce)
        when(notificationService.annonceSuivieDejaNotifiee(
                logement.getOwner().getId(), logement.getId(), etudiant.getId()))
                .thenReturn(true);

        service.suivre(EMAIL, logement.getId(), null);

        // Pas de spam « retirer / re-suivre » : aucune nouvelle alerte
        verify(notificationService, never())
                .notify(any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotNotifyWhenCandidatureCreatedByContacting() {
        stubUser();
        stubNoPhoto();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(candidatureRepository.findByUserIdAndLogementId(any(), any()))
                .thenReturn(Optional.empty());
        when(candidatureRepository.save(any(Candidature.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Candidature créée directement en CONTACTE = l'étudiant a écrit au
        // propriétaire : il reçoit déjà le message, pas de notification en double
        service.suivre(EMAIL, logement.getId(), CandidatureStatut.CONTACTE);

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldNotNotifyWhenOwnerFollowsOwnListing() {
        // Le propriétaire suit sa propre annonce : personne à prévenir
        User proprio = logement.getOwner();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(proprio));
        stubNoPhoto();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(candidatureRepository.findByUserIdAndLogementId(any(), any()))
                .thenReturn(Optional.empty());
        when(candidatureRepository.save(any(Candidature.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.suivre(EMAIL, logement.getId(), null);

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldNotNotifyWhenAlreadyFollowed() {
        stubUser();
        stubNoPhoto();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(candidatureRepository.findByUserIdAndLogementId(any(), any()))
                .thenReturn(Optional.of(candidatureExistante(CandidatureStatut.A_CONTACTER, etudiant)));

        // Re-suivre une annonce déjà suivie ne re-notifie pas le propriétaire
        service.suivre(EMAIL, logement.getId(), null);

        verifyNoInteractions(notificationService);
    }

    // Idempotence : re-suivre ne duplique pas et n'écrase pas le statut choisi
    @Test
    void shouldNotDuplicateNorResetStatutWhenAlreadyFollowed() {
        stubUser();
        stubNoPhoto();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(candidatureRepository.findByUserIdAndLogementId(etudiant.getId(), logement.getId()))
                .thenReturn(Optional.of(candidatureExistante(CandidatureStatut.VISITEE, etudiant)));

        CandidatureResponse res =
                service.suivre(EMAIL, logement.getId(), CandidatureStatut.CONTACTE);

        assertThat(res.statut()).isEqualTo(CandidatureStatut.VISITEE);
        verify(candidatureRepository, never()).save(any());
    }

    // Seule progression automatique tolérée : « repérée » → « contactée »
    @Test
    void shouldUpgradeFromAContacterToContacte() {
        stubUser();
        stubNoPhoto();
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));
        when(candidatureRepository.findByUserIdAndLogementId(etudiant.getId(), logement.getId()))
                .thenReturn(Optional.of(candidatureExistante(CandidatureStatut.A_CONTACTER, etudiant)));
        when(candidatureRepository.save(any(Candidature.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CandidatureResponse res =
                service.suivre(EMAIL, logement.getId(), CandidatureStatut.CONTACTE);

        assertThat(res.statut()).isEqualTo(CandidatureStatut.CONTACTE);
    }

    @Test
    void shouldThrowWhenLogementNotFound() {
        stubUser();
        when(logementRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suivre(EMAIL, UUID.randomUUID(), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Logement introuvable");
    }

    // ─── Mise à jour du statut ────────────────────────────────────────────────

    @Test
    void shouldUpdateStatutAndNote() {
        Candidature c = candidatureExistante(CandidatureStatut.CONTACTE, etudiant);
        stubUser();
        stubNoPhoto();
        when(candidatureRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(candidatureRepository.save(any(Candidature.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CandidatureResponse res = service.updateStatut(
                EMAIL, c.getId(), CandidatureStatut.VISITE_PREVUE, "Visite jeudi 18h");

        assertThat(res.statut()).isEqualTo(CandidatureStatut.VISITE_PREVUE);
        assertThat(res.note()).isEqualTo("Visite jeudi 18h");
    }

    // Sécurité : le suivi est personnel, on ne touche pas à celui d'un autre (IDOR)
    @Test
    void shouldRejectUpdateOfSomeoneElsesCandidature() {
        User autre = User.builder().id(UUID.randomUUID()).email("autre@studup.fr")
                .firstName("Autre").lastName("User").build();
        Candidature c = candidatureExistante(CandidatureStatut.CONTACTE, autre);
        stubUser();
        when(candidatureRepository.findById(c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.updateStatut(
                EMAIL, c.getId(), CandidatureStatut.VISITEE, null))
                .isInstanceOf(UnauthorizedException.class);
        verify(candidatureRepository, never()).save(any());
    }

    // ─── Suppression ──────────────────────────────────────────────────────────

    @Test
    void shouldDeleteOwnCandidature() {
        Candidature c = candidatureExistante(CandidatureStatut.SANS_SUITE, etudiant);
        stubUser();
        when(candidatureRepository.findById(c.getId())).thenReturn(Optional.of(c));

        service.delete(EMAIL, c.getId());

        verify(candidatureRepository).delete(c);
    }

    @Test
    void shouldRejectDeleteByNonOwner() {
        User autre = User.builder().id(UUID.randomUUID()).email("autre@studup.fr")
                .firstName("Autre").lastName("User").build();
        Candidature c = candidatureExistante(CandidatureStatut.CONTACTE, autre);
        stubUser();
        when(candidatureRepository.findById(c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.delete(EMAIL, c.getId()))
                .isInstanceOf(UnauthorizedException.class);
        verify(candidatureRepository, never()).delete(any());
    }

    // ─── Liste ────────────────────────────────────────────────────────────────

    @Test
    void shouldReturnMesCandidatures() {
        stubUser();
        stubNoPhoto();
        when(candidatureRepository.findByUserIdOrderByUpdatedAtDesc(etudiant.getId()))
                .thenReturn(List.of(candidatureExistante(CandidatureStatut.CONTACTE, etudiant)));

        List<CandidatureResponse> res = service.getMesCandidatures(EMAIL);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).statut()).isEqualTo(CandidatureStatut.CONTACTE);
    }
}
