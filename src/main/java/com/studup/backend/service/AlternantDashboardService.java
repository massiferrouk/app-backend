package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.AccordSummaryResponse;
import com.studup.backend.model.dto.response.AlternantDashboardResponse;
import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.LogementRepository;
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
    private final LogementRepository logementRepository;

    public AlternantDashboardService(UserRepository userRepository,
                                     AccordRepository accordRepository,
                                     LogementRepository logementRepository) {
        this.userRepository = userRepository;
        this.accordRepository = accordRepository;
        this.logementRepository = logementRepository;
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

        // 3. Économies estimées : loyer moyen des logements × mois d'échanges terminés
        List<Accord> termines = accordRepository.findAccordsTerminesEchange(userId);
        BigDecimal economies = calculerEconomies(userId, termines);

        log.info("Dashboard alternant — userId={} prochains={} enAttente={} economies={}",
                userId, prochains.size(), enAttente.size(), economies);

        return new AlternantDashboardResponse(prochains, enAttente, economies, termines.size());
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

    private BigDecimal calculerEconomies(UUID userId, List<Accord> termines) {
        if (termines.isEmpty()) return BigDecimal.ZERO;

        // Loyer moyen des logements de l'utilisateur (source : ses logements publiés)
        List<Logement> logements = logementRepository.findByOwnerId(userId);
        if (logements.isEmpty()) return BigDecimal.ZERO;

        BigDecimal loyerMoyen = logements.stream()
                .filter(l -> l.getLoyer() != null)
                .map(Logement::getLoyer)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logements.size()), 2, RoundingMode.HALF_UP);

        if (loyerMoyen.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        // Pour chaque accord terminé : économie = loyer_moyen × nb_mois
        BigDecimal total = BigDecimal.ZERO;
        for (Accord a : termines) {
            if (a.getDateDebut() != null && a.getDateFin() != null) {
                long mois = ChronoUnit.MONTHS.between(a.getDateDebut(), a.getDateFin());
                if (mois > 0) {
                    total = total.add(loyerMoyen.multiply(BigDecimal.valueOf(mois)));
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
