package com.yuniv.backend.service;

import com.yuniv.backend.exception.DuplicateEmailException;
import com.yuniv.backend.model.dto.request.RegisterRequest;
import com.yuniv.backend.model.dto.response.UserResponse;
import com.yuniv.backend.model.entity.User;
import com.yuniv.backend.model.enums.UserRole;
import com.yuniv.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailConfirmationService emailConfirmationService;

    @InjectMocks
    private AuthService authService;

    // Cas nominal : inscription réussie
    @Test
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr",
                "motdepasse123",
                "Alice",
                "Martin",
                UserRole.ALTERNANT
        );

        // Simule : email pas encore en BDD
        when(userRepository.existsByEmail("alice@yuniv.fr")).thenReturn(false);

        // Simule : BCrypt retourne un hash
        when(passwordEncoder.encode("motdepasse123")).thenReturn("$2a$10$hashedpassword");

        // Simule : la BDD retourne l'utilisateur sauvegardé avec un ID
        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@yuniv.fr")
                .passwordHash("$2a$10$hashedpassword")
                .firstName("Alice")
                .lastName("Martin")
                .role(UserRole.ALTERNANT)
                .isVerified(false)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = authService.register(request);

        // Vérifie la réponse
        assertThat(response.email()).isEqualTo("alice@yuniv.fr");
        assertThat(response.firstName()).isEqualTo("Alice");
        assertThat(response.role()).isEqualTo(UserRole.ALTERNANT);
        assertThat(response.isVerified()).isFalse();

        // Vérifie que le mot de passe a bien été hashé
        verify(passwordEncoder).encode("motdepasse123");

        // Vérifie que l'email de confirmation a bien été déclenché
        verify(emailConfirmationService).sendConfirmationEmail(any(User.class));

        // Vérifie que l'utilisateur a bien été sauvegardé en BDD
        verify(userRepository).save(any(User.class));
    }

    // Cas d'erreur : email déjà utilisé
    @Test
    void shouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "alice@yuniv.fr",
                "motdepasse123",
                "Alice",
                "Martin",
                UserRole.ALTERNANT
        );

        // Simule : email déjà présent en BDD
        when(userRepository.existsByEmail("alice@yuniv.fr")).thenReturn(true);

        // Vérifie que DuplicateEmailException est levée
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Un compte existe déjà avec cet email");

        // Vérifie qu'on n'a jamais touché à la BDD ni encodé le mot de passe
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(emailConfirmationService, never()).sendConfirmationEmail(any());
    }
}
