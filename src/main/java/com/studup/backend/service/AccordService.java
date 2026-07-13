package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.AccordRequest;
import com.studup.backend.model.dto.response.AccordResponse;
import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccordService {

    private final AccordRepository accordRepository;
    private final UserRepository userRepository;
    private final AlternantProfileRepository profileRepository;

    public AccordService(AccordRepository accordRepository,
                         UserRepository userRepository,
                         AlternantProfileRepository profileRepository) {
        this.accordRepository = accordRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    // Crée un accord en statut EN_ATTENTE
    @Transactional
    public AccordResponse createAccord(String initiatorEmail, AccordRequest request) {
        User initiator = userRepository.findByEmail(initiatorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        if (initiator.getId().equals(request.receiverId())) {
            throw new UnauthorizedException("Vous ne pouvez pas envoyer un accord à vous-même");
        }

        // Vérifie que le destinataire existe
        userRepository.findById(request.receiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Destinataire introuvable"));

        // Dates : soit fournies par le client, soit déduites de la période
        // commune des deux alternances (l'app ne demande pas de dates).
        Periode periode = resolvePeriode(request, initiator.getId(), request.receiverId());

        Accord accord = Accord.builder()
                .initiatorId(initiator.getId())
                .receiverId(request.receiverId())
                .type(request.type())
                .statut(AccordStatut.EN_ATTENTE)
                .dateDebut(periode.debut())
                .dateFin(periode.fin())
                .logementAId(request.logementAId())
                .logementBId(request.logementBId())
                .montantLoyer(request.montantLoyer())
                .messageInitial(request.messageInitial())
                .build();

        return AccordResponse.from(accordRepository.save(accord));
    }

    /** Période d'un accord — début et fin. */
    private record Periode(LocalDate debut, LocalDate fin) {}

    /**
     * Détermine la période de l'accord.
     * Si le client fournit les deux dates, on les respecte.
     * Sinon on calcule la période commune des deux alternances :
     * début = plus tardif des deux débuts, fin = plus précoce des deux fins.
     */
    private Periode resolvePeriode(AccordRequest request, UUID initiatorId, UUID receiverId) {
        if (request.dateDebut() != null && request.dateFin() != null) {
            if (!request.dateDebut().isBefore(request.dateFin())) {
                throw new IllegalArgumentException("La date de début doit être avant la date de fin");
            }
            return new Periode(request.dateDebut(), request.dateFin());
        }

        Optional<AlternantProfile> initiatorProfile = profileRepository.findByUserId(initiatorId);
        Optional<AlternantProfile> receiverProfile = profileRepository.findByUserId(receiverId);

        if (initiatorProfile.isEmpty() || receiverProfile.isEmpty()) {
            throw new IllegalArgumentException(
                    "Impossible de déterminer la période : les deux profils alternants sont requis");
        }

        // Période commune = intersection des deux périodes d'alternance
        LocalDate debut = max(initiatorProfile.get().getDateDebut(), receiverProfile.get().getDateDebut());
        LocalDate fin = min(initiatorProfile.get().getDateFin(), receiverProfile.get().getDateFin());

        if (!debut.isBefore(fin)) {
            throw new IllegalArgumentException(
                    "Les périodes d'alternance ne se chevauchent pas");
        }
        return new Periode(debut, fin);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    // Accepte un accord — uniquement le destinataire
    @Transactional
    public AccordResponse acceptAccord(UUID accordId, String userEmail) {
        Accord accord = getAccordOrThrow(accordId);
        User user = getUserOrThrow(userEmail);

        if (!accord.getReceiverId().equals(user.getId())) {
            throw new UnauthorizedException("Seul le destinataire peut accepter un accord");
        }
        checkStatutEnAttente(accord);

        accord.setStatut(AccordStatut.ACCEPTE);
        return AccordResponse.from(accordRepository.save(accord));
    }

    // Refuse un accord — uniquement le destinataire
    @Transactional
    public AccordResponse refuseAccord(UUID accordId, String userEmail) {
        Accord accord = getAccordOrThrow(accordId);
        User user = getUserOrThrow(userEmail);

        if (!accord.getReceiverId().equals(user.getId())) {
            throw new UnauthorizedException("Seul le destinataire peut refuser un accord");
        }
        checkStatutEnAttente(accord);

        accord.setStatut(AccordStatut.REFUSE);
        return AccordResponse.from(accordRepository.save(accord));
    }

    // Annule un accord — initiateur ou destinataire, tant que EN_ATTENTE ou ACCEPTE
    @Transactional
    public AccordResponse cancelAccord(UUID accordId, String userEmail) {
        Accord accord = getAccordOrThrow(accordId);
        User user = getUserOrThrow(userEmail);

        boolean estParticipant = accord.getInitiatorId().equals(user.getId())
                || accord.getReceiverId().equals(user.getId());

        if (!estParticipant) {
            throw new UnauthorizedException("Vous n'êtes pas participant à cet accord");
        }

        if (accord.getStatut() == AccordStatut.TERMINE
                || accord.getStatut() == AccordStatut.ANNULE
                || accord.getStatut() == AccordStatut.REFUSE) {
            throw new UnauthorizedException(
                    "Impossible d'annuler un accord au statut : " + accord.getStatut());
        }

        accord.setStatut(AccordStatut.ANNULE);
        return AccordResponse.from(accordRepository.save(accord));
    }

    // Historique des accords de l'utilisateur connecté
    @Transactional(readOnly = true)
    public Page<AccordResponse> getMesAccords(String userEmail, Pageable pageable) {
        User user = getUserOrThrow(userEmail);
        return accordRepository.findByUserId(user.getId(), pageable)
                .map(AccordResponse::from);
    }

    // ─── Méthodes privées ─────────────────────────────────────────────────────

    private Accord getAccordOrThrow(UUID accordId) {
        return accordRepository.findById(accordId)
                .orElseThrow(() -> new ResourceNotFoundException("Accord introuvable : " + accordId));
    }

    private User getUserOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private void checkStatutEnAttente(Accord accord) {
        if (accord.getStatut() != AccordStatut.EN_ATTENTE) {
            throw new UnauthorizedException(
                    "Cet accord ne peut plus être modifié — statut actuel : " + accord.getStatut());
        }
    }
}
