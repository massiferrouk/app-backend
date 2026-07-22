package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.AccordSummaryResponse;
import com.studup.backend.model.dto.response.AlternantDashboardResponse;
import com.studup.backend.model.dto.response.MatchingSuggestionResponse;
import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AlternantDashboardService {

    private static final Logger log = LoggerFactory.getLogger(AlternantDashboardService.class);
    private static final long EXPIRATION_HEURES = 72L;
    private static final int SEMAINES_HORIZON = 8;

    private final UserRepository userRepository;
    private final AccordRepository accordRepository;
    private final MatchingService matchingService;

    public AlternantDashboardService(UserRepository userRepository,
                                     AccordRepository accordRepository,
                                     MatchingService matchingService) {
        this.userRepository = userRepository;
        this.accordRepository = accordRepository;
        this.matchingService = matchingService;
    }

    @Transactional(readOnly = true)
    public AlternantDashboardResponse getDashboard(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        UUID userId = user.getId();
        LocalDate today = LocalDate.now();
        LocalDate horizon8Semaines = today.plusWeeks(SEMAINES_HORIZON);

        // 1. Prochains accords sur 8 semaines
        List<AccordSummaryResponse> prochains = accordRepository
                .findProchainAccords(userId, horizon8Semaines)
                .stream()
                .map(a -> toSummary(a, userId, null))
                .toList();

        // 2. Accords en attente de réponse (countdown expiration)
        OffsetDateTime now = OffsetDateTime.now();
        List<AccordSummaryResponse> enAttente = accordRepository
                .findAccordsEnAttenteForReceiver(userId)
                .stream()
                .map(a -> {
                    long heuresEcoulees = ChronoUnit.HOURS.between(a.getCreatedAt(), now);
                    long heuresRestantes = Math.max(0L, EXPIRATION_HEURES - heuresEcoulees);
                    return toSummary(a, userId, heuresRestantes);
                })
                .toList();

        // 3. KPIs vivants (APP-120) : nombre de matches et meilleure économie
        // POSSIBLE. Remplacent « économies réalisées » et « échanges terminés »,
        // qui comptaient des accords TERMINE jamais atteints → 0 à vie.
        List<MatchingSuggestionResponse> suggestions = matchingService.getSuggestions(userEmail);
        BigDecimal economiePossibleMax = suggestions.stream()
                .map(MatchingSuggestionResponse::economieMensuelle)
                .filter(e -> e != null)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        log.info("Dashboard alternant — userId={} prochains={} enAttente={} matches={}",
                userId, prochains.size(), enAttente.size(), suggestions.size());

        return new AlternantDashboardResponse(
                prochains, enAttente, economiePossibleMax, suggestions.size());
    }

    // ─── Méthodes privées ─────────────────────────────────────────────────────

    private AccordSummaryResponse toSummary(Accord a, UUID userId, Long heuresAvantExpiration) {
        UUID partnerId = a.getInitiatorId().equals(userId)
                ? a.getReceiverId()
                : a.getInitiatorId();
        return new AccordSummaryResponse(
                a.getId(), a.getType(), a.getStatut(),
                a.getDateDebut(), a.getDateFin(),
                partnerId, heuresAvantExpiration);
    }
}
