package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.response.AdminUserResponse;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
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

    // ─── suspendUser ──────────────────────────────────────────────────────────

    @Test
    void shouldSuspendUserAndRevokeTokens() {
        when(userRepository.findById(alternantUser.getId())).thenReturn(Optional.of(alternantUser));
        when(userRepository.save(any())).thenReturn(alternantUser);

        AdminUserResponse result = adminService.suspendUser(alternantUser.getId(), "admin@studup.fr");

        assertThat(alternantUser.getIsActive()).isFalse();
        verify(jwtBlacklistService).revokeAllForUser(alternantUser.getId());
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
