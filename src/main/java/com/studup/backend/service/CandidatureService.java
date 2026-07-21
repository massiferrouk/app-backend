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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Suivi des candidatures logement (APP-117) — le « Trello intégré ».
 * Les statuts sont pilotés par l'utilisateur, le service ne les déduit jamais.
 */
@Service
public class CandidatureService {

    private static final Logger log = LoggerFactory.getLogger(CandidatureService.class);

    private final CandidatureRepository candidatureRepository;
    private final LogementRepository logementRepository;
    private final UserRepository userRepository;
    private final PhotoLogementRepository photoRepository;
    private final MinioService minioService;
    private final NotificationService notificationService;

    public CandidatureService(CandidatureRepository candidatureRepository,
                              LogementRepository logementRepository,
                              UserRepository userRepository,
                              PhotoLogementRepository photoRepository,
                              MinioService minioService,
                              NotificationService notificationService) {
        this.candidatureRepository = candidatureRepository;
        this.logementRepository = logementRepository;
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.minioService = minioService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<CandidatureResponse> getMesCandidatures(String email) {
        User user = findUser(email);
        return candidatureRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream()
                .map(c -> CandidatureResponse.from(c, coverPhoto(c.getLogement().getId())))
                .toList();
    }

    /**
     * Suit une annonce. IDEMPOTENT : re-suivre la même annonce ne crée pas de
     * doublon (contrainte unique en base) et ne réinitialise pas le statut que
     * l'utilisateur a défini.
     *
     * Seule exception : passer de A_CONTACTER à CONTACTE. Ça arrive quand
     * l'annonce était déjà repérée et que l'utilisateur clique « Contacter » —
     * on enregistre alors le fait qu'il a réellement contacté, ce qui est
     * précisément le besoin (« j'oublie si j'ai déjà postulé »).
     */
    @Transactional
    public CandidatureResponse suivre(String email, UUID logementId, CandidatureStatut statutDemande) {
        User user = findUser(email);
        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable"));

        CandidatureStatut statut =
                statutDemande != null ? statutDemande : CandidatureStatut.A_CONTACTER;

        Candidature candidature = candidatureRepository
                .findByUserIdAndLogementId(user.getId(), logementId)
                .map(existante -> {
                    if (existante.getStatut() == CandidatureStatut.A_CONTACTER
                            && statut == CandidatureStatut.CONTACTE) {
                        existante.setStatut(CandidatureStatut.CONTACTE);
                        return candidatureRepository.save(existante);
                    }
                    return existante;
                })
                .orElseGet(() -> {
                    Candidature creee = candidatureRepository.save(Candidature.builder()
                            .user(user)
                            .logement(logement)
                            .statut(statut)
                            .build());
                    notifierProprietaire(logement, statut, user);
                    return creee;
                });

        return CandidatureResponse.from(candidature, coverPhoto(logementId));
    }

    /**
     * Prévient le propriétaire qu'un étudiant a mis son annonce en favori (APP-119).
     *
     * Deux garde-fous volontaires :
     * - uniquement à la CRÉATION et uniquement en statut A_CONTACTER, donc au
     *   clic sur « Suivre cette annonce ». Quand la candidature naît d'un
     *   « Contacter » (statut CONTACTE direct), on ne notifie pas : le
     *   propriétaire reçoit déjà le message, ce serait un doublon.
     * - on ne transmet QUE la ville. Le statut de suivi est le tableau de bord
     *   privé de l'étudiant, le propriétaire ne doit jamais le connaître.
     *
     * Jamais bloquant : un échec de notification ne doit pas empêcher de suivre.
     */
    private void notifierProprietaire(Logement logement, CandidatureStatut statut, User etudiant) {
        if (statut != CandidatureStatut.A_CONTACTER) return;
        // Se suivre soi-même (proprio sur sa propre annonce) ne notifie rien
        if (logement.getOwner() != null
                && logement.getOwner().getId().equals(etudiant.getId())) return;

        try {
            UUID proprioId = logement.getOwner().getId();

            // Anti-spam « retirer / re-suivre » (APP-119) : un même étudiant ne
            // déclenche qu'UNE alerte par annonce, à vie. Dédupliqué sur le
            // couple (étudiant, annonce) — un autre étudiant notifie bien.
            if (notificationService.annonceSuivieDejaNotifiee(
                    proprioId, logement.getId(), etudiant.getId())) {
                return;
            }

            // Le payload persiste ce couple pour la déduplication future.
            // L'identité de l'étudiant reste interne : le template affiché au
            // propriétaire ne dit toujours que « un étudiant » + la ville.
            String payload = "{\"logementId\": \"" + logement.getId()
                    + "\", \"etudiantId\": \"" + etudiant.getId() + "\"}";

            notificationService.notify(
                    proprioId,
                    NotificationType.ANNONCE_SUIVIE,
                    Map.of("ville", logement.getVille()),
                    "logement/" + logement.getId(),
                    payload);
        } catch (RuntimeException e) {
            log.warn("Notification ANNONCE_SUIVIE non envoyée pour logementId={}",
                    logement.getId(), e);
        }
    }

    /** Fait évoluer le statut/la note. Ownership vérifié : c'est un suivi PERSONNEL. */
    @Transactional
    public CandidatureResponse updateStatut(String email, UUID candidatureId,
                                            CandidatureStatut statut, String note) {
        Candidature candidature = findOwned(email, candidatureId);
        candidature.setStatut(statut);
        candidature.setNote(note);
        candidature = candidatureRepository.save(candidature);
        return CandidatureResponse.from(candidature, coverPhoto(candidature.getLogement().getId()));
    }

    @Transactional
    public void delete(String email, UUID candidatureId) {
        candidatureRepository.delete(findOwned(email, candidatureId));
    }

    // ─── Méthodes privées ─────────────────────────────────────────────────────

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    /**
     * Charge une candidature en vérifiant qu'elle appartient bien à l'appelant.
     * Sans ce contrôle, n'importe qui pourrait modifier le suivi d'un autre
     * utilisateur en devinant son identifiant (faille IDOR).
     */
    private Candidature findOwned(String email, UUID candidatureId) {
        User user = findUser(email);
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidature introuvable"));
        if (!candidature.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Cette candidature ne vous appartient pas");
        }
        return candidature;
    }

    /** URL signée de la photo de couverture (liste de 0 ou 1 élément). */
    private List<String> coverPhoto(UUID logementId) {
        return photoRepository.findFileKeysByLogementId(logementId).stream()
                .findFirst()
                .map(key -> List.of(minioService.generatePresignedUrl(key)))
                .orElse(List.of());
    }
}
