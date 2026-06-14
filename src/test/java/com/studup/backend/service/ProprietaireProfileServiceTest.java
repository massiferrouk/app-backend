package com.studup.backend.service;

import com.studup.backend.exception.ProfileAlreadyExistsException;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateProprietaireProfileRequest;
import com.studup.backend.model.dto.response.ProprietaireProfileResponse;
import com.studup.backend.model.entity.ProprietaireProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.ProprietaireProfileRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProprietaireProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProprietaireProfileRepository profileRepository;

    @InjectMocks
    private ProprietaireProfileService service;

    private User fakeUser;

    @BeforeEach
    void setUp() {
        fakeUser = User.builder()
                .id(UUID.randomUUID())
                .email("pierre@studup.fr")
                .passwordHash("hashed")
                .role(UserRole.PROPRIETAIRE)
                .firstName("Pierre")
                .lastName("Dupont")
                .isVerified(false)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── Création du profil ───────────────────────────────────────────────────

    @Test
    void shouldCreateProfile() {
        CreateProprietaireProfileRequest request = new CreateProprietaireProfileRequest(
                "0612345678", "12 rue de la Paix", "Paris", "75001", null
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);
        when(profileRepository.save(any(ProprietaireProfile.class))).thenAnswer(invocation -> {
            ProprietaireProfile p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setIsVerified(false);
            p.setCreatedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());
            return p;
        });

        ProprietaireProfileResponse response = service.createProfile("pierre@studup.fr", request);

        assertThat(response.phone()).isEqualTo("0612345678");
        assertThat(response.adresse()).isEqualTo("12 rue de la Paix");
        assertThat(response.ville()).isEqualTo("Paris");
        assertThat(response.codePostal()).isEqualTo("75001");
        assertThat(response.siret()).isNull();
        assertThat(response.isVerified()).isFalse();
    }

    @Test
    void shouldCreateProfileWithSiret() {
        CreateProprietaireProfileRequest request = new CreateProprietaireProfileRequest(
                "0612345678", "12 rue de la Paix", "Paris", "75001", "12345678901234"
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);
        when(profileRepository.save(any(ProprietaireProfile.class))).thenAnswer(invocation -> {
            ProprietaireProfile p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setIsVerified(false);
            p.setCreatedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());
            return p;
        });

        ProprietaireProfileResponse response = service.createProfile("pierre@studup.fr", request);

        assertThat(response.siret()).isEqualTo("12345678901234");
    }

    // ─── Récupération du profil ───────────────────────────────────────────────

    @Test
    void shouldGetProfile() {
        ProprietaireProfile existingProfile = ProprietaireProfile.builder()
                .id(UUID.randomUUID())
                .user(fakeUser)
                .phone("0612345678")
                .adresse("12 rue de la Paix")
                .ville("Paris")
                .codePostal("75001")
                .siret(null)
                .isVerified(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.findByUserId(fakeUser.getId())).thenReturn(Optional.of(existingProfile));

        ProprietaireProfileResponse response = service.getProfile("pierre@studup.fr");

        assertThat(response.userId()).isEqualTo(fakeUser.getId());
        assertThat(response.ville()).isEqualTo("Paris");
    }

    // ─── Cas d'erreur ─────────────────────────────────────────────────────────

    @Test
    void shouldThrowWhenProfileAlreadyExists() {
        CreateProprietaireProfileRequest request = new CreateProprietaireProfileRequest(
                "0612345678", "12 rue de la Paix", "Paris", "75001", null
        );

        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile("pierre@studup.fr", request))
                .isInstanceOf(ProfileAlreadyExistsException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        CreateProprietaireProfileRequest request = new CreateProprietaireProfileRequest(
                "0612345678", "12 rue de la Paix", "Paris", "75001", null
        );

        when(userRepository.findByEmail("inconnu@studup.fr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProfile("inconnu@studup.fr", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldThrowWhenProfileNotFoundOnGet() {
        when(userRepository.findByEmail("pierre@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.findByUserId(fakeUser.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile("pierre@studup.fr"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Profil propriétaire introuvable");
    }
}
