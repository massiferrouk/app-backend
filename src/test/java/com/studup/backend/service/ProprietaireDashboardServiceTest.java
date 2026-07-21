package com.studup.backend.service;

import com.studup.backend.model.dto.response.ProprietaireDashboardResponse;
import com.studup.backend.model.entity.ConversationParticipant;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.CandidatureRepository;
import com.studup.backend.repository.ConversationParticipantRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProprietaireDashboardServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private LogementRepository logementRepository;
    @Mock private AccordRepository accordRepository;
    @Mock private CandidatureRepository candidatureRepository;
    @Mock private ConversationParticipantRepository participantRepository;

    @InjectMocks private ProprietaireDashboardService service;

    private User proprio;

    @BeforeEach
    void setUp() {
        proprio = User.builder()
                .id(UUID.randomUUID()).email("proprio@studup.fr")
                .firstName("Marc").lastName("P").role(UserRole.PROPRIETAIRE)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    private Logement logement(UUID id, LogementStatut statut) {
        return Logement.builder()
                .id(id).ville("Paris").adresse("12 rue de la Paix").codePostal("75001")
                .type(LogementType.STUDIO).statut(statut).loyer(BigDecimal.valueOf(800))
                .nbPieces(1).isVerified(false).isMeuble(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    private ConversationParticipant participation() {
        return ConversationParticipant.builder()
                .id(UUID.randomUUID()).conversationId(UUID.randomUUID())
                .userId(proprio.getId()).joinedAt(OffsetDateTime.now()).build();
    }

    // ─── KPIs vivants : étudiants intéressés + conversations (APP-119) ───────
    // Remplacent « taux d'occupation » et « locataires actifs », qui reposaient
    // sur des accords EN_COURS jamais atteints et affichaient 0 à vie.

    @Test
    void shouldCountInterestedStudentsAndConversations() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(userRepository.findByEmail("proprio@studup.fr")).thenReturn(Optional.of(proprio));
        when(logementRepository.findByOwnerId(proprio.getId()))
                .thenReturn(List.of(logement(id1, LogementStatut.ACTIF),
                        logement(id2, LogementStatut.ACTIF)));
        when(accordRepository.findOccupiedLogementIds(any())).thenReturn(List.of());
        // 3 étudiants distincts suivent au moins une des deux annonces
        when(candidatureRepository.countDistinctUsersByLogementIds(List.of(id1, id2)))
                .thenReturn(3L);
        when(participantRepository.findByUserId(proprio.getId()))
                .thenReturn(List.of(participation(), participation()));

        ProprietaireDashboardResponse result = service.getDashboard("proprio@studup.fr");

        assertThat(result.nbLogementsTotaux()).isEqualTo(2);
        assertThat(result.nbLogementsActifs()).isEqualTo(2);
        assertThat(result.nbEtudiantsInteresses()).isEqualTo(3);
        assertThat(result.nbConversations()).isEqualTo(2);
    }

    // ─── Le drapeau isOccupe des résumés reste alimenté (alerte « vacant ») ───

    @Test
    void shouldFlagOccupiedLogementsInSummaries() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(userRepository.findByEmail("proprio@studup.fr")).thenReturn(Optional.of(proprio));
        when(logementRepository.findByOwnerId(proprio.getId()))
                .thenReturn(List.of(logement(id1, LogementStatut.ACTIF),
                        logement(id2, LogementStatut.ACTIF)));
        when(accordRepository.findOccupiedLogementIds(any())).thenReturn(List.of(id1));
        when(candidatureRepository.countDistinctUsersByLogementIds(any())).thenReturn(0L);
        when(participantRepository.findByUserId(any())).thenReturn(List.of());

        ProprietaireDashboardResponse result = service.getDashboard("proprio@studup.fr");

        assertThat(result.logements().stream()
                .filter(l -> l.id().equals(id1)).findFirst().get().isOccupe()).isTrue();
        assertThat(result.logements().stream()
                .filter(l -> l.id().equals(id2)).findFirst().get().isOccupe()).isFalse();
    }

    // ─── aucun logement → dashboard vide cohérent, sans requête inutile ──────

    @Test
    void shouldReturnEmptyDashboardWhenNoLogements() {
        when(userRepository.findByEmail("proprio@studup.fr")).thenReturn(Optional.of(proprio));
        when(logementRepository.findByOwnerId(proprio.getId())).thenReturn(List.of());
        when(participantRepository.findByUserId(proprio.getId())).thenReturn(List.of());

        ProprietaireDashboardResponse result = service.getDashboard("proprio@studup.fr");

        assertThat(result.nbLogementsTotaux()).isEqualTo(0);
        assertThat(result.nbEtudiantsInteresses()).isEqualTo(0);
        assertThat(result.nbConversations()).isEqualTo(0);
        assertThat(result.logements()).isEmpty();
        // Sans annonce, on n'interroge pas les candidatures (IN () invalide)
        verify(candidatureRepository, never()).countDistinctUsersByLogementIds(any());
    }
}
