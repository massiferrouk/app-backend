package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.AdminDashboardResponse;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.LogementReportRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.MessageReportRepository;
import com.studup.backend.repository.MotInterditRepository;
import com.studup.backend.repository.RefreshTokenRepository;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.repository.UserSpecification;
import com.studup.backend.security.JwtBlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LogementRepository logementRepository;
    private final MessageReportRepository reportRepository;
    private final LogementReportRepository reportLogementRepository;
    private final MotInterditRepository motInterditRepository;
    private final JwtBlacklistService jwtBlacklistService;

    public AdminService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        LogementRepository logementRepository,
                        MessageReportRepository reportRepository,
                        LogementReportRepository reportLogementRepository,
                        MotInterditRepository motInterditRepository,
                        JwtBlacklistService jwtBlacklistService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.logementRepository = logementRepository;
        this.reportRepository = reportRepository;
        this.reportLogementRepository = reportLogementRepository;
        this.motInterditRepository = motInterditRepository;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    /**
     * Tableau de bord de l'administration (APP-121).
     *
     * Chaque chiffre est calculé à la demande, sans table d'agrégats : à
     * l'échelle du projet, une dizaine de COUNT indexés coûtent moins cher
     * qu'un cache à maintenir cohérent. Les deux répartitions passent par un
     * GROUP BY plutôt qu'un COUNT par valeur.
     */
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        Map<UserRole, Long> parRole = versMap(userRepository.countGroupByRole(), UserRole.class);
        Map<LogementStatut, Long> parStatut =
                versMap(logementRepository.countGroupByStatut(), LogementStatut.class);

        OffsetDateTime maintenant = OffsetDateTime.now();

        return new AdminDashboardResponse(
                parRole.values().stream().mapToLong(Long::longValue).sum(),
                parRole,
                userRepository.countByIsActiveFalseAndDeletedAtIsNull(),
                userRepository.countByDeletedAtIsNotNull(),
                userRepository.countByCreatedAtAfter(maintenant.minusDays(7)),
                userRepository.countByCreatedAtAfter(maintenant.minusDays(30)),
                parStatut.values().stream().mapToLong(Long::longValue).sum(),
                parStatut,
                reportRepository.countPendingReports(),
                reportLogementRepository.countPendingReports(),
                motInterditRepository.count());
    }

    /**
     * Convertit le résultat d'un GROUP BY en map complète.
     *
     * Les valeurs absentes sont forcées à zéro : sans ça, un rôle sans aucun
     * compte disparaîtrait de la réponse et le tableau de bord afficherait un
     * trou au lieu d'un « 0 ».
     */
    private <E extends Enum<E>> Map<E, Long> versMap(List<Object[]> lignes, Class<E> type) {
        Map<E, Long> resultat = new EnumMap<>(type);
        for (E valeur : type.getEnumConstants()) {
            resultat.put(valeur, 0L);
        }
        for (Object[] ligne : lignes) {
            resultat.put(type.cast(ligne[0]), (Long) ligne[1]);
        }
        return resultat;
    }

    /**
     * Liste filtrable des comptes.
     *
     * Les filtres absents n'ajoutent aucune clause — voir UserSpecification
     * pour la raison : la version en @Query échouait contre PostgreSQL sur le
     * typage des paramètres d'ENUM natif.
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(UserRole role, Boolean isActive, Pageable pageable) {
        Specification<User> filtres = Specification.allOf();
        if (role != null) {
            filtres = filtres.and(UserSpecification.roleEgale(role));
        }
        if (isActive != null) {
            filtres = filtres.and(UserSpecification.estActif(isActive));
        }
        return userRepository.findAll(filtres, pageable).map(AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse suspendUser(UUID userId, String adminEmail) {
        User user = findUserOrThrow(userId);
        checkNotAdmin(user);

        user.setIsActive(false);
        revoquerTousLesAcces(userId);

        log.info("Utilisateur {} suspendu par {}", userId, adminEmail);
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse banUser(UUID userId, String adminEmail) {
        User user = findUserOrThrow(userId);
        checkNotAdmin(user);

        user.setIsActive(false);
        user.setDeletedAt(java.time.OffsetDateTime.now());
        revoquerTousLesAcces(userId);

        log.info("Utilisateur {} banni par {}", userId, adminEmail);
        return AdminUserResponse.from(userRepository.save(user));
    }

    /**
     * Lève une suspension ou un bannissement (APP-121).
     *
     * Réactiver ne se limite pas à remettre isActive à true : la clé Redis
     * posée à la suspension vit 24 h et fait refuser le compte par
     * JwtAuthFilter. Sans sa suppression, l'utilisateur resterait bloqué
     * alors que l'admin le croit rétabli.
     *
     * Les refresh tokens restent révoqués : l'utilisateur devra se
     * reconnecter, ce qui est le comportement attendu après une exclusion.
     */
    @Transactional
    public AdminUserResponse reactivateUser(UUID userId, String adminEmail) {
        User user = findUserOrThrow(userId);

        user.setIsActive(true);
        user.setDeletedAt(null);
        jwtBlacklistService.restoreUser(userId);

        log.info("Utilisateur {} réactivé par {}", userId, adminEmail);
        return AdminUserResponse.from(userRepository.save(user));
    }

    /**
     * Coupe l'accès immédiatement, sur les deux canaux — l'un sans l'autre
     * ne suffit pas :
     * - Redis coupe les access tokens déjà émis (sinon le compte reste servi
     *   jusqu'à leur expiration) ;
     * - la révocation des refresh tokens empêche d'en obtenir de nouveaux
     *   (sinon le bannissement se contourne indéfiniment par /auth/refresh).
     */
    private void revoquerTousLesAcces(UUID userId) {
        jwtBlacklistService.revokeAllForUser(userId);
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));
    }

    // Un admin ne peut pas suspendre ou bannir un autre admin
    private void checkNotAdmin(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new UnauthorizedException("Impossible de suspendre ou bannir un administrateur");
        }
    }
}
