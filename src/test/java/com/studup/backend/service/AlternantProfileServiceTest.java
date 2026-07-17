package com.studup.backend.service;

import com.studup.backend.algorithm.ScheduleGenerator;
import com.studup.backend.exception.ProfileAlreadyExistsException;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateAlternantProfileRequest;
import com.studup.backend.model.dto.response.AlternantProfileResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.PremiereSemaine;
import com.studup.backend.model.enums.RythmeAlternance;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.JourFerieRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlternantProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AlternantProfileRepository profileRepository;
    @Mock private AlternanceScheduleRepository scheduleRepository;
    @Mock private JourFerieRepository jourFerieRepository;

    // @Spy : on utilise la vraie implémentation de ScheduleGenerator (pas de dépendances externes)
    @Spy private ScheduleGenerator scheduleGenerator = new ScheduleGenerator();

    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlternantProfileService service;

    private User fakeUser;

    @BeforeEach
    void setUp() {
        fakeUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@studup.fr")
                .passwordHash("hashed")
                .role(UserRole.ALTERNANT)
                .firstName("Alice")
                .lastName("Martin")
                .isVerified(false)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // ─── Création du profil ───────────────────────────────────────────────────

    @Test
    void shouldCreateProfileAndGenerateSchedule() {
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "Lyon", "ESIEA", "Thales",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1, null
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);
        when(jourFerieRepository.findByPaysAndDateJourBetween(anyString(), any(), any()))
                .thenReturn(Set.of());

        // Simule le comportement de la BDD : save() retourne le profil avec un ID généré
        when(profileRepository.save(any(AlternantProfile.class))).thenAnswer(invocation -> {
            AlternantProfile p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCreatedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());
            return p;
        });

        when(scheduleRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AlternantProfileResponse response = service.createProfile("alice@studup.fr", request);

        assertThat(response.villeA()).isEqualTo("Paris");
        assertThat(response.villeB()).isEqualTo("Lyon");
        assertThat(response.rythme()).isEqualTo(RythmeAlternance.SEMAINE_1_1);
        // 52 semaines entre le 01/09/2025 et le 31/08/2026
        assertThat(response.scheduleWeeksGenerated()).isEqualTo(52);
    }

    @Test
    void shouldApplyLegacyDefaultPremiereSemaineWhenAbsent() {
        // Ancien client : pas de premiereSemaine envoyée avec un rythme 3-1
        // → le service applique le défaut historique ENTREPRISE (APP-110)
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "Lyon", "ESIEA", "Thales",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_3_1, null
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);
        when(jourFerieRepository.findByPaysAndDateJourBetween(anyString(), any(), any()))
                .thenReturn(Set.of());
        when(profileRepository.save(any(AlternantProfile.class))).thenAnswer(invocation -> {
            AlternantProfile p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCreatedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());
            return p;
        });
        when(scheduleRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        AlternantProfileResponse response = service.createProfile("alice@studup.fr", request);

        assertThat(response.premiereSemaine()).isEqualTo(PremiereSemaine.ENTREPRISE);
    }

    @Test
    void shouldKeepExplicitPremiereSemaine() {
        // Nouveau client : premiereSemaine ECOLE explicite avec un rythme 3-1
        // (l'inverse du défaut) → le choix de l'utilisateur est conservé
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "Lyon", "ESIEA", "Thales",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_3_1, PremiereSemaine.ECOLE
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);
        when(jourFerieRepository.findByPaysAndDateJourBetween(anyString(), any(), any()))
                .thenReturn(Set.of());
        when(profileRepository.save(any(AlternantProfile.class))).thenAnswer(invocation -> {
            AlternantProfile p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCreatedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());
            return p;
        });
        when(scheduleRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        AlternantProfileResponse response = service.createProfile("alice@studup.fr", request);

        assertThat(response.premiereSemaine()).isEqualTo(PremiereSemaine.ECOLE);
    }

    @Test
    void shouldGenerateCorrectLabelsForSemaine11() {
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "Lyon", "ESIEA", "Thales",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1, null
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);
        when(jourFerieRepository.findByPaysAndDateJourBetween(anyString(), any(), any()))
                .thenReturn(Set.of());
        when(profileRepository.save(any(AlternantProfile.class))).thenAnswer(invocation -> {
            AlternantProfile p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCreatedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());
            return p;
        });

        // On capture la liste de semaines envoyée à saveAll pour l'inspecter
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AlternanceSchedule>> captor = ArgumentCaptor.forClass(List.class);
        when(scheduleRepository.saveAll(captor.capture())).thenAnswer(i -> i.getArgument(0));

        service.createProfile("alice@studup.fr", request);

        List<AlternanceSchedule> generatedSchedule = captor.getValue();

        // En rythme SEMAINE_1_1 : semaine 0 = A, semaine 1 = B, semaine 2 = A...
        assertThat(generatedSchedule.get(0).getLabel()).isEqualTo("A");
        assertThat(generatedSchedule.get(1).getLabel()).isEqualTo("B");
        assertThat(generatedSchedule.get(2).getLabel()).isEqualTo("A");
        assertThat(generatedSchedule.get(3).getLabel()).isEqualTo("B");
        // Toutes les semaines paires sont A, impaires sont B
        assertThat(generatedSchedule).allSatisfy(s ->
                assertThat(s.getIsOverridden()).isFalse()
        );
    }

    @Test
    void shouldStopGeneratingScheduleAfterDateFin() {
        // Contrat court : seulement 4 semaines
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "Lyon", "ESIEA", "Thales",
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 26),
                RythmeAlternance.SEMAINE_1_1, null
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);
        when(jourFerieRepository.findByPaysAndDateJourBetween(anyString(), any(), any()))
                .thenReturn(Set.of());
        when(profileRepository.save(any(AlternantProfile.class))).thenAnswer(invocation -> {
            AlternantProfile p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCreatedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());
            return p;
        });
        when(scheduleRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        AlternantProfileResponse response = service.createProfile("alice@studup.fr", request);

        // 4 semaines entre le 01/09 et le 26/09/2025
        assertThat(response.scheduleWeeksGenerated()).isEqualTo(4);
    }

    // ─── Cas d'erreur ─────────────────────────────────────────────────────────

    @Test
    void shouldThrowWhenProfileAlreadyExists() {
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "Lyon", "ESIEA", "Thales",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1, null
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile("alice@studup.fr", request))
                .isInstanceOf(ProfileAlreadyExistsException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    void shouldThrowWhenDateDebutIsAfterDateFin() {
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "Lyon", "ESIEA", "Thales",
                LocalDate.of(2026, 8, 31), LocalDate.of(2025, 9, 1), // dates inversées
                RythmeAlternance.SEMAINE_1_1, null
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.createProfile("alice@studup.fr", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("antérieure");
    }

    @Test
    void shouldThrowWhenVillesAreTheSame() {
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "paris", "ESIEA", "Thales", // même ville, casse différente
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1, null
        );

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(fakeUser));
        when(profileRepository.existsByUserId(fakeUser.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.createProfile("alice@studup.fr", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("différentes");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        CreateAlternantProfileRequest request = new CreateAlternantProfileRequest(
                "Paris", "Lyon", "ESIEA", "Thales",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31),
                RythmeAlternance.SEMAINE_1_1, null
        );

        when(userRepository.findByEmail("inconnu@studup.fr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProfile("inconnu@studup.fr", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
