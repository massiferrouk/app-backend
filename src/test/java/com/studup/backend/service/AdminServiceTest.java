package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.dto.response.AdminDashboardResponse;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.LogementReportRepository;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.MessageReportRepository;
import com.studup.backend.repository.MotInterditRepository;
import com.studup.backend.repository.RefreshTokenRepository;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.security.JwtBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LogementRepository logementRepository;
    @Mock private MessageReportRepository reportRepository;
    @Mock private LogementReportRepository reportLogementRepository;
    @Mock private MotInterditRepository motInterditRepository;
    @Mock private JwtBlacklistService jwtBlacklistService;

    @InjectMocks
    private AdminService adminService;

    private User alternantUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        alternantUser = User.builder()
                .id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("Bob").lastName("Dupont")
                .role(UserRole.ALTERNANT).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        adminUser = User.builder()
                .id(UUID.randomUUID()).email("admin@studup.fr")
                .firstName("Admin").lastName("StudUp")
                .role(UserRole.ADMIN).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── listUsers ────────────────────────────────────────────────────────────

    @Test
    void shouldReturnPaginatedUsers() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> page = new PageImpl<>(List.of(alternantUser), pageable, 1);

        when(userRepository.findAllFiltered(null, null, pageable)).thenReturn(page);

        Page<AdminUserResponse> result = adminService.listUsers(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("bob@studup.fr");
    }

    @Test
    void shouldFilterByRole() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> page = new PageImpl<>(List.of(alternantUser), pageable, 1);

        when(userRepository.findAllFiltered(eq(UserRole.ALTERNANT), any(), eq(pageable)))
                .thenReturn(page);

        Page<AdminUserResponse> result = adminService.listUsers(UserRole.ALTERNANT, null, pageable);

        assertThat(result.getContent().get(0).role()).isEqualTo(UserRole.ALTERNANT);
    }

    // ─── Tableau de bord (APP-121) ────────────────────────────────────────────

    @Test
    void shouldAgregerLesChiffresDuTableauDeBord() {
        when(userRepository.countGroupByRole()).thenReturn(List.of(
                new Object[]{UserRole.ALTERNANT, 12L},
                new Object[]{UserRole.ETUDIANT, 30L}));
        when(logementRepository.countGroupByStatut()).thenReturn(List.of(
                new Object[]{LogementStatut.ACTIF, 8L},
                new Object[]{LogementStatut.SUSPENDU, 2L}));
        when(userRepository.countByIsActiveFalseAndDeletedAtIsNull()).thenReturn(3L);
        when(userRepository.countByDeletedAtIsNotNull()).thenReturn(1L);
        when(userRepository.countByCreatedAtAfter(any())).thenReturn(5L);
        when(reportRepository.countPendingReports()).thenReturn(4L);
        when(reportLogementRepository.countPendingReports()).thenReturn(2L);
        when(motInterditRepository.count()).thenReturn(7L);

        AdminDashboardResponse d = adminService.getDashboard();

        // Les totaux sont déduits des répartitions, pas comptés une seconde fois
        assertThat(d.totalComptes()).isEqualTo(42L);
        assertThat(d.totalAnnonces()).isEqualTo(10L);
        assertThat(d.comptesSuspendus()).isEqualTo(3L);
        assertThat(d.comptesBannis()).isEqualTo(1L);
        assertThat(d.signalementsEnAttente()).isEqualTo(4L);
        assertThat(d.annoncesSignalees()).isEqualTo(2L);
        assertThat(d.motsInterdits()).isEqualTo(7L);
    }

    @Test
    void shouldAfficherZeroPourLesValeursSansAucuneLigne() {
        // Le GROUP BY ne renvoie rien pour un rôle sans compte : sans
        // complétion, le tableau de bord afficherait un trou au lieu d'un 0.
        when(userRepository.countGroupByRole())
                .thenReturn(List.<Object[]>of(new Object[]{UserRole.ETUDIANT, 5L}));
        when(logementRepository.countGroupByStatut()).thenReturn(List.<Object[]>of());

        AdminDashboardResponse d = adminService.getDashboard();

        assertThat(d.comptesParRole()).containsKeys(UserRole.values());
        assertThat(d.comptesParRole().get(UserRole.PROPRIETAIRE)).isZero();
        assertThat(d.annoncesParStatut()).containsKeys(LogementStatut.values());
        assertThat(d.totalAnnonces()).isZero();
    }

    // ─── reactivateUser (APP-121) ─────────────────────────────────────────────

    @Test
    void shouldReactivateUserAndLiftRevocation() {
        alternantUser.setIsActive(false);
        alternantUser.setDeletedAt(OffsetDateTime.now());
        when(userRepository.findById(alternantUser.getId())).thenReturn(Optional.of(alternantUser));
        when(userRepository.save(any())).thenReturn(alternantUser);

        adminService.reactivateUser(alternantUser.getId(), "admin@studup.fr");

        assertThat(alternantUser.getIsActive()).isTrue();
        assertThat(alternantUser.getDeletedAt()).isNull();
        // Sans cette levee, le compte resterait refuse jusqu'a 24 h alors que
        // l'admin le croit retabli.
        verify(jwtBlacklistService).restoreUser(alternantUser.getId());
        verify(userRepository).save(alternantUser);
    }

    @Test
    void shouldThrowWhenReactivatingNonExistentUser() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.reactivateUser(unknownId, "admin@studup.fr"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── suspendUser ──────────────────────────────────────────────────────────

    @Test
    void shouldSuspendUserAndRevokeTokens() {
        when(userRepository.findById(alternantUser.getId())).thenReturn(Optional.of(alternantUser));
        when(userRepository.save(any())).thenReturn(alternantUser);

        AdminUserResponse result = adminService.suspendUser(alternantUser.getId(), "admin@studup.fr");

        assertThat(alternantUser.getIsActive()).isFalse();
        // Les deux canaux : sans la révocation des refresh tokens, le compte
        // suspendu se redonnait un access token neuf via /auth/refresh.
        verify(jwtBlacklistService).revokeAllForUser(alternantUser.getId());
        verify(refreshTokenRepository).revokeAllByUserId(alternantUser.getId());
        verify(userRepository).save(alternantUser);
    }

    @Test
    void shouldThrowWhenSuspendingNonExistentUser() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.suspendUser(unknownId, "admin@studup.fr"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Utilisateur introuvable");
    }

    @Test
    void shouldThrowWhenSuspendingAdmin() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> adminService.suspendUser(adminUser.getId(), "admin@studup.fr"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("administrateur");
    }

    // ─── banUser ──────────────────────────────────────────────────────────────

    @Test
    void shouldBanUserAndSetDeletedAt() {
        when(userRepository.findById(alternantUser.getId())).thenReturn(Optional.of(alternantUser));
        when(userRepository.save(any())).thenReturn(alternantUser);

        adminService.banUser(alternantUser.getId(), "admin@studup.fr");

        assertThat(alternantUser.getIsActive()).isFalse();
        assertThat(alternantUser.getDeletedAt()).isNotNull();
        verify(jwtBlacklistService).revokeAllForUser(alternantUser.getId());
    }

    @Test
    void shouldThrowWhenBanningAdmin() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> adminService.banUser(adminUser.getId(), "admin@studup.fr"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
