package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.UserResponse;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    private User userWithRole(UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("massi@studup.fr")
                .passwordHash("hash")
                .firstName("Massi")
                .lastName("F")
                .role(role)
                .isVerified(true)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void shouldChangeModeFromEtudiantToAlternant() {
        User user = userWithRole(UserRole.ETUDIANT);
        when(userRepository.findByEmail("massi@studup.fr")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.changeMode("massi@studup.fr", UserRole.ALTERNANT);

        assertThat(res.role()).isEqualTo(UserRole.ALTERNANT);
        assertThat(user.getRole()).isEqualTo(UserRole.ALTERNANT);
    }

    @Test
    void shouldChangeModeFromAlternantToEtudiant() {
        User user = userWithRole(UserRole.ALTERNANT);
        when(userRepository.findByEmail("massi@studup.fr")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.changeMode("massi@studup.fr", UserRole.ETUDIANT);

        assertThat(res.role()).isEqualTo(UserRole.ETUDIANT);
    }

    // Un étudiant/alternant ne peut pas se transformer en propriétaire (autre compte).
    @Test
    void shouldRejectChangeToProprietaire() {
        User user = userWithRole(UserRole.ETUDIANT);
        when(userRepository.findByEmail("massi@studup.fr")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeMode("massi@studup.fr", UserRole.PROPRIETAIRE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("étudiant et alternant");
        verify(userRepository, never()).save(any());
    }

    // Un propriétaire ne peut pas basculer en mode étudiant (acteur distinct).
    @Test
    void shouldRejectChangeFromProprietaire() {
        User user = userWithRole(UserRole.PROPRIETAIRE);
        when(userRepository.findByEmail("massi@studup.fr")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeMode("massi@studup.fr", UserRole.ALTERNANT))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    // Sécurité : personne ne s'auto-promeut ADMIN via le changement de mode.
    @Test
    void shouldRejectSelfPromotionToAdmin() {
        User user = userWithRole(UserRole.ALTERNANT);
        when(userRepository.findByEmail("massi@studup.fr")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeMode("massi@studup.fr", UserRole.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeMode("ghost@studup.fr", UserRole.ALTERNANT))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
