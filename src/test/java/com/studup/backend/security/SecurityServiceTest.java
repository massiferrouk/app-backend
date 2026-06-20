package com.studup.backend.security;

import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.LogementRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private LogementRepository logementRepository;
    @Mock private Authentication authentication;

    @InjectMocks
    private SecurityService securityService;

    private User owner;
    private User otherUser;
    private Logement logement;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(UUID.randomUUID())
                .email("pierre@studup.fr")
                .role(UserRole.PROPRIETAIRE)
                .build();

        otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("autre@studup.fr")
                .role(UserRole.ALTERNANT)
                .build();

        logement = Logement.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .build();
    }

    // ─── isLogementOwner ─────────────────────────────────────────────────────

    @Test
    void shouldReturnTrueWhenUserIsOwner() {
        when(authentication.getName()).thenReturn("pierre@studup.fr");
        when(authentication.getAuthorities()).thenReturn(List.of());
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(owner));
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));

        boolean result = securityService.isLogementOwner(logement.getId(), authentication);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserIsNotOwner() {
        when(authentication.getName()).thenReturn("autre@studup.fr");
        when(authentication.getAuthorities()).thenReturn(List.of());
        when(userRepository.findByEmail("autre@studup.fr")).thenReturn(Optional.of(otherUser));
        when(logementRepository.findById(logement.getId())).thenReturn(Optional.of(logement));

        boolean result = securityService.isLogementOwner(logement.getId(), authentication);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenLogementNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(authentication.getAuthorities()).thenReturn(List.of());
        when(authentication.getName()).thenReturn("pierre@studup.fr");
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(owner));
        when(logementRepository.findById(unknownId)).thenReturn(Optional.empty());

        boolean result = securityService.isLogementOwner(unknownId, authentication);

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUserIsAdmin() {
        // Un admin bypass l'ownership check — peut modifier n'importe quel logement
        when(authentication.getAuthorities()).thenReturn(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        boolean result = securityService.isLogementOwner(logement.getId(), authentication);

        // L'admin ne déclenche aucune requête BDD — il est autorisé directement
        assertThat(result).isTrue();
    }

    // ─── isSelf ──────────────────────────────────────────────────────────────

    @Test
    void shouldReturnTrueWhenUserActsOnOwnProfile() {
        when(authentication.getName()).thenReturn("pierre@studup.fr");
        when(authentication.getAuthorities()).thenReturn(List.of());
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(owner));

        boolean result = securityService.isSelf(owner.getId(), authentication);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserActsOnOtherProfile() {
        when(authentication.getName()).thenReturn("autre@studup.fr");
        when(authentication.getAuthorities()).thenReturn(List.of());
        when(userRepository.findByEmail("autre@studup.fr")).thenReturn(Optional.of(otherUser));

        boolean result = securityService.isSelf(owner.getId(), authentication);

        assertThat(result).isFalse();
    }
}
