package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.LogementSummaryResponse;
import com.studup.backend.model.dto.response.ProprietaireDashboardResponse;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.repository.AccordRepository;
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

    public ProprietaireDashboardService(UserRepository userRepository,
                                        LogementRepository logementRepository,
                                        AccordRepository accordRepository) {
        this.userRepository = userRepository;
        this.logementRepository = logementRepository;
        this.accordRepository = accordRepository;
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
        List<UUID> ids = logements.stream().map(Logement::getId).toList();
        Set<UUID> occupiedIds = ids.isEmpty()
                ? Set.of()
                : Set.copyOf(accordRepository.findOccupiedLogementIds(ids));

        double tauxOccupation = nbActifs == 0
                ? 0.0
                : Math.round((double) occupiedIds.size() / nbActifs * 100 * 10.0) / 10.0;

        List<LogementSummaryResponse> summaries = logements.stream()
                .map(l -> LogementSummaryResponse.from(l, occupiedIds.contains(l.getId())))
                .toList();

        log.info("Dashboard propriétaire — userId={} nbLogements={} taux={}%",
                user.getId(), logements.size(), tauxOccupation);

        return new ProprietaireDashboardResponse(
                logements.size(),
                (int) nbActifs,
                occupiedIds.size(),
                tauxOccupation,
                summaries
        );
    }
}
