package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.LogementSummaryResponse;
import com.studup.backend.model.dto.response.ProprietaireDashboardResponse;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.CandidatureRepository;
import com.studup.backend.repository.ConversationParticipantRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProprietaireDashboardService {

    private static final Logger log = LoggerFactory.getLogger(ProprietaireDashboardService.class);

    private final UserRepository userRepository;
    private final LogementRepository logementRepository;
    private final AccordRepository accordRepository;
    private final CandidatureRepository candidatureRepository;
    private final ConversationParticipantRepository participantRepository;

    public ProprietaireDashboardService(UserRepository userRepository,
                                        LogementRepository logementRepository,
                                        AccordRepository accordRepository,
                                        CandidatureRepository candidatureRepository,
                                        ConversationParticipantRepository participantRepository) {
        this.userRepository = userRepository;
        this.logementRepository = logementRepository;
        this.accordRepository = accordRepository;
        this.candidatureRepository = candidatureRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional(readOnly = true)
    public ProprietaireDashboardResponse getDashboard(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        List<Logement> logements = logementRepository.findByOwnerId(user.getId());

        long nbActifs = logements.stream()
                .filter(l -> l.getStatut() == LogementStatut.ACTIF)
                .count();

        // Récupère les IDs de logements occupés par un accord EN_COURS
        // (alimente le drapeau isOccupe des résumés / l'alerte « sans locataire »)
        List<UUID> ids = logements.stream().map(Logement::getId).toList();
        Set<UUID> occupiedIds = ids.isEmpty()
                ? Set.of()
                : Set.copyOf(accordRepository.findOccupiedLogementIds(ids));

        // KPIs vivants (APP-119) : remplacent « taux d'occupation » et
        // « locataires actifs », calculés depuis des accords EN_COURS jamais
        // atteints — ils affichaient 0 à vie. Ici, des chiffres qui bougent
        // avec l'usage réel : intérêt des étudiants et discussions ouvertes.
        int nbEtudiantsInteresses = ids.isEmpty()
                ? 0
                : (int) candidatureRepository.countDistinctUsersByLogementIds(ids);
        int nbConversations = participantRepository.findByUserId(user.getId()).size();

        List<LogementSummaryResponse> summaries = logements.stream()
                .map(l -> LogementSummaryResponse.from(l, occupiedIds.contains(l.getId())))
                .toList();

        log.info("Dashboard propriétaire — userId={} nbLogements={} interesses={}",
                user.getId(), logements.size(), nbEtudiantsInteresses);

        return new ProprietaireDashboardResponse(
                logements.size(),
                (int) nbActifs,
                nbEtudiantsInteresses,
                nbConversations,
                summaries
        );
    }
}
