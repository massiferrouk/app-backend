package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.AccordRequest;
import com.studup.backend.model.dto.response.AccordResponse;
import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AccordRepository;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccordServiceTest {

    @Mock private AccordRepository accordRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private AccordService accordService;

    private User initiator;
    private User receiver;
    private Accord accordEnAttente;

    @BeforeEach
    void setUp() {
        initiator = User.builder()
                .id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        receiver = User.builder()
                .id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("B").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        accordEnAttente = Accord.builder()
                .id(UUID.randomUUID())
                .initiatorId(initiator.getId())
                .receiverId(receiver.getId())
                .type(AccordType.ECHANGE_TOTAL)
                .statut(AccordStatut.EN_ATTENTE)
                .dateDebut(LocalDate.now().plusDays(10))
                .dateFin(LocalDate.now().plusMonths(6))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── Création d'un accord ─────────────────────────────────────────────────

    @Test
    void shouldCreateAccordAndNotifyRecipient() {
        AccordRequest request = new AccordRequest(
                receiver.getId(), AccordType.ECHANGE_TOTAL,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(6),
                null, null, null, "Bonjour, échange possible ?"
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(initiator));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(accordRepository.save(any())).thenReturn(accordEnAttente);

        AccordResponse response = accordService.createAccord("alice@studup.fr", request);

        assertThat(response.statut()).isEqualTo(AccordStatut.EN_ATTENTE);
        assertThat(response.initiatorId()).isEqualTo(initiator.getId());
        assertThat(response.receiverId()).isEqualTo(receiver.getId());
    }

    @Test
    void shouldRejectAccordToSelf() {
        AccordRequest request = new AccordRequest(
                initiator.getId(), AccordType.ECHANGE_TOTAL,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(6),
                null, null, null, null
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(initiator));

        assertThatThrownBy(() -> accordService.createAccord("alice@studup.fr", request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("vous-même");
    }

    // ─── Acceptation ──────────────────────────────────────────────────────────

    @Test
    void shouldAcceptAccordByReceiver() {
        Accord accordAccepte = Accord.builder()
                .id(accordEnAttente.getId())
                .initiatorId(initiator.getId()).receiverId(receiver.getId())
                .type(AccordType.ECHANGE_TOTAL).statut(AccordStatut.ACCEPTE)
                .dateDebut(accordEnAttente.getDateDebut()).dateFin(accordEnAttente.getDateFin())
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        when(accordRepository.findById(accordEnAttente.getId()))
                .thenReturn(Optional.of(accordEnAttente));
        when(userRepository.findByEmail("bob@studup.fr")).thenReturn(Optional.of(receiver));
        when(accordRepository.save(any())).thenReturn(accordAccepte);

        AccordResponse response = accordService.acceptAccord(accordEnAttente.getId(), "bob@studup.fr");

        assertThat(response.statut()).isEqualTo(AccordStatut.ACCEPTE);
    }

    @Test
    void shouldRejectAcceptByNonReceiver() {
        when(accordRepository.findById(accordEnAttente.getId()))
                .thenReturn(Optional.of(accordEnAttente));
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(initiator));

        assertThatThrownBy(() -> accordService.acceptAccord(accordEnAttente.getId(), "alice@studup.fr"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("destinataire");
    }

    // ─── Annulation ───────────────────────────────────────────────────────────

    @Test
    void shouldCancelAccordByInitiator() {
        Accord accordAnnule = Accord.builder()
                .id(accordEnAttente.getId())
                .initiatorId(initiator.getId()).receiverId(receiver.getId())
                .type(AccordType.ECHANGE_TOTAL).statut(AccordStatut.ANNULE)
                .dateDebut(accordEnAttente.getDateDebut()).dateFin(accordEnAttente.getDateFin())
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        when(accordRepository.findById(accordEnAttente.getId()))
                .thenReturn(Optional.of(accordEnAttente));
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(initiator));
        when(accordRepository.save(any())).thenReturn(accordAnnule);

        AccordResponse response = accordService.cancelAccord(accordEnAttente.getId(), "alice@studup.fr");

        assertThat(response.statut()).isEqualTo(AccordStatut.ANNULE);
    }

    @Test
    void shouldRejectCancelByNonParticipant() {
        User stranger = User.builder().id(UUID.randomUUID()).email("stranger@studup.fr")
                .role(UserRole.ALTERNANT).isActive(true).isVerified(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        when(accordRepository.findById(accordEnAttente.getId()))
                .thenReturn(Optional.of(accordEnAttente));
        when(userRepository.findByEmail("stranger@studup.fr")).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> accordService.cancelAccord(accordEnAttente.getId(), "stranger@studup.fr"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("participant");
    }

    // ─── Historique ───────────────────────────────────────────────────────────

    @Test
    void shouldReturnMesAccords() {
        Page<Accord> page = new PageImpl<>(List.of(accordEnAttente));
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(initiator));
        when(accordRepository.findByUserId(initiator.getId(), PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AccordResponse> result = accordService.getMesAccords("alice@studup.fr", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).statut()).isEqualTo(AccordStatut.EN_ATTENTE);
    }

    // ─── Accord introuvable ───────────────────────────────────────────────────

    @Test
    void shouldThrowWhenAccordNotFound() {
        UUID fakeId = UUID.randomUUID();
        when(accordRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accordService.acceptAccord(fakeId, "bob@studup.fr"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
