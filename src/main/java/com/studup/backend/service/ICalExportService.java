package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.IcalTokenResponse;
import com.studup.backend.model.entity.Accord;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.IcalToken;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.IcalTokenRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ICalExportService {

    private final UserRepository userRepository;
    private final IcalTokenRepository icalTokenRepository;
    private final AlternantProfileRepository alternantProfileRepository;
    private final AlternanceScheduleRepository alternanceScheduleRepository;
    private final AccordRepository accordRepository;
    private final String baseUrl;

    public ICalExportService(
            UserRepository userRepository,
            IcalTokenRepository icalTokenRepository,
            AlternantProfileRepository alternantProfileRepository,
            AlternanceScheduleRepository alternanceScheduleRepository,
            AccordRepository accordRepository,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository = userRepository;
        this.icalTokenRepository = icalTokenRepository;
        this.alternantProfileRepository = alternantProfileRepository;
        this.alternanceScheduleRepository = alternanceScheduleRepository;
        this.accordRepository = accordRepository;
        this.baseUrl = baseUrl;
    }

    // Génère le fichier .ics pour l'utilisateur connecté
    public String generateIcal(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return buildIcal(user);
    }

    // Génère le fichier .ics via le token d'abonnement public (sans authentification)
    @Transactional
    public String generateIcalByToken(String token) {
        IcalToken icalToken = icalTokenRepository.findByTokenAndIsActiveTrue(token)
                .orElseThrow(() -> new ResourceNotFoundException("Token d'abonnement invalide ou expiré"));

        icalToken.setLastUsed(OffsetDateTime.now());
        icalTokenRepository.save(icalToken);

        User user = userRepository.findById(icalToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return buildIcal(user);
    }

    // Récupère le token existant ou en crée un nouveau
    @Transactional
    public IcalTokenResponse getOrCreateToken(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        String token = icalTokenRepository.findByUserId(user.getId())
                .filter(IcalToken::getIsActive)
                .map(IcalToken::getToken)
                .orElseGet(() -> {
                    IcalToken newToken = IcalToken.builder()
                            .userId(user.getId())
                            .token(generateSecureToken())
                            .isActive(true)
                            .build();
                    return icalTokenRepository.save(newToken).getToken();
                });

        return new IcalTokenResponse(token, buildSubscribeUrl(token));
    }

    // Révoque l'ancien token et en génère un nouveau
    @Transactional
    public IcalTokenResponse regenerateToken(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        icalTokenRepository.findByUserId(user.getId()).ifPresent(existing -> {
            existing.setIsActive(false);
            icalTokenRepository.save(existing);
        });

        IcalToken newToken = IcalToken.builder()
                .userId(user.getId())
                .token(generateSecureToken())
                .isActive(true)
                .build();
        String token = icalTokenRepository.save(newToken).getToken();
        return new IcalTokenResponse(token, buildSubscribeUrl(token));
    }

    // ─── Construction du contenu iCal ────────────────────────────────────────

    private String buildIcal(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//StudUp//CalendrierAlternance//FR\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:StudUp - Calendrier alternance\r\n");

        // Semaines du calendrier d'alternance
        alternantProfileRepository.findByUserId(user.getId()).ifPresent(profile -> {
            List<AlternanceSchedule> schedules =
                    alternanceScheduleRepository.findByProfileIdOrderBySemaineAsc(profile.getId());
            for (AlternanceSchedule s : schedules) {
                String ville = "A".equals(s.getLabel()) ? profile.getVilleA() : profile.getVilleB();
                String type = "A".equals(s.getLabel()) ? "École" : "Entreprise";
                LocalDate debut = s.getSemaine();
                LocalDate fin = debut.plusDays(5);
                sb.append(buildVEvent(
                        s.getId().toString() + "@studup.fr",
                        debut, fin,
                        ville + " (" + type + ")"));
            }
        });

        // Accords confirmés (EN_COURS ou ACCEPTE)
        List<Accord> accords = accordRepository.findActiveAccordsForUser(user.getId());
        for (Accord a : accords) {
            String summary = switch (a.getType()) {
                case ECHANGE_TOTAL -> "Échange total (StudUp)";
                case ECHANGE_PARTIEL -> "Échange partiel (StudUp)";
                case COLOCATION_TOURNANTE -> "Colocation tournante (StudUp)";
                case LOCATION_CLASSIQUE -> "Location (StudUp)";
                default -> "Accord StudUp";
            };
            sb.append(buildVEvent(
                    "accord-" + a.getId() + "@studup.fr",
                    a.getDateDebut(), a.getDateFin().plusDays(1),
                    summary));
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private String buildVEvent(String uid, LocalDate dtStart, LocalDate dtEnd, String summary) {
        return "BEGIN:VEVENT\r\n" +
               "UID:" + uid + "\r\n" +
               "DTSTART;VALUE=DATE:" + formatDate(dtStart) + "\r\n" +
               "DTEND;VALUE=DATE:" + formatDate(dtEnd) + "\r\n" +
               "SUMMARY:" + summary + "\r\n" +
               "END:VEVENT\r\n";
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String buildSubscribeUrl(String token) {
        return baseUrl + "/api/v1/calendrier/subscribe/" + token;
    }

    // 32 bytes aléatoires cryptographiquement sûrs → 64 caractères hex
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
