package com.studup.backend.service;

import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.exception.UnauthorizedException;
import com.studup.backend.model.dto.request.OverrideScheduleRequest;
import com.studup.backend.model.dto.response.AlternanceScheduleResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.CompatibiliteType;
import com.studup.backend.model.enums.RythmeAlternance;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendrierServiceTest {

    @Mock private AlternantProfileRepository profileRepository;
    @Mock private AlternanceScheduleRepository scheduleRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompatibilityCalculator calculator;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private CalendrierService calendrierService;

    private UUID userId1;
    private UUID userId2;
    private AlternantProfile profileA;
    private AlternantProfile profileB;

    // Données pour les tests override
    private User userAlice;
    private AlternantProfile profileAlice;
    private AlternanceSchedule schedule;
    private final LocalDate NEXT_MONDAY = LocalDate.now().plusWeeks(1).with(DayOfWeek.MONDAY);

    @BeforeEach
    void setUp() {
        userId1 = UUID.randomUUID();
        userId2 = UUID.randomUUID();

        User userA = User.builder()
                .id(userId1).email("alice@studup.fr")
                .firstName("Alice").lastName("Martin")
                .role(UserRole.ALTERNANT).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        User userB = User.builder()
                .id(userId2).email("bob@studup.fr")
                .firstName("Bob").lastName("Dupont")
                .role(UserRole.ALTERNANT).isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        profileA = AlternantProfile.builder()
                .id(UUID.randomUUID()).user(userA)
                .villeA("Paris").villeB("Lyon")
                .rythme(RythmeAlternance.SEMAINE_3_1).build();

        profileB = AlternantProfile.builder()
                .id(UUID.randomUUID()).user(userB)
                .villeA("Lyon").villeB("Paris")
                .rythme(RythmeAlternance.SEMAINE_3_1).build();

        // Setup pour les tests override
        userAlice = User.builder()
                .id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        profileAlice = AlternantProfile.builder()
                .id(UUID.randomUUID()).user(userAlice)
                .villeA("Paris").villeB("Lyon")
                .dateDebut(LocalDate.now().minusMonths(6))
                .dateFin(LocalDate.now().plusMonths(6)).build();

        schedule = AlternanceSchedule.builder()
                .id(UUID.randomUUID()).profile(profileAlice)
                .semaine(NEXT_MONDAY).label("A").isOverridden(false).build();
    }

    // ─── Retourne les semaines colorisées ─────────────────────────────────────

    @Test
    void shouldReturnColorCodedWeeks() {
        when(profileRepository.findByUserId(userId1)).thenReturn(Optional.of(profileA));
        when(profileRepository.findByUserId(userId2)).thenReturn(Optional.of(profileB));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());

        List<SemaineCompatibilite> semaines = List.of(
                SemaineCompatibilite.of(LocalDate.of(2026, 1, 5), "Lyon", "Paris", CompatibiliteType.ECHANGE),
                SemaineCompatibilite.of(LocalDate.of(2026, 1, 12), "Lyon", "Paris", CompatibiliteType.ECHANGE),
                SemaineCompatibilite.of(LocalDate.of(2026, 1, 19), "Lyon", "Paris", CompatibiliteType.ECHANGE),
                SemaineCompatibilite.of(LocalDate.of(2026, 1, 26), "Paris", "Lyon", CompatibiliteType.ECHANGE)
        );

        MatchingResult result = new MatchingResult(1.0, AccordType.ECHANGE_TOTAL,
                false, null, semaines, 4, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, "4 sem d'échange");

        when(calculator.calculate(any(), any(), any(), any())).thenReturn(result);

        List<SemaineCompatibilite> response = calendrierService.getCalendrierCompatibilite(userId1, userId2);

        assertThat(response).hasSize(4);
        assertThat(response.get(0).type()).isEqualTo(CompatibiliteType.ECHANGE);
        assertThat(response.get(0).couleurHex()).isEqualTo("#27AE60");
        assertThat(response.get(0).label()).isEqualTo("Échange");
    }

    // ─── Couleurs correctes selon le type ─────────────────────────────────────

    @Test
    void shouldReturnCorrectColorsForEachWeekType() {
        when(profileRepository.findByUserId(userId1)).thenReturn(Optional.of(profileA));
        when(profileRepository.findByUserId(userId2)).thenReturn(Optional.of(profileB));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());

        LocalDate semaine = LocalDate.of(2026, 1, 5);
        List<SemaineCompatibilite> semaines = List.of(
                SemaineCompatibilite.of(semaine, "Lyon", "Paris", CompatibiliteType.ECHANGE),
                SemaineCompatibilite.of(semaine.plusWeeks(1), "Paris", "Paris", CompatibiliteType.CHEVAUCHEMENT),
                SemaineCompatibilite.of(semaine.plusWeeks(2), "Paris", "Paris", CompatibiliteType.COLOCATION)
        );

        MatchingResult result = new MatchingResult(0.33, AccordType.ECHANGE_PARTIEL,
                false, null, semaines, 1, 1, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, "résumé");

        when(calculator.calculate(any(), any(), any(), any())).thenReturn(result);

        List<SemaineCompatibilite> response = calendrierService.getCalendrierCompatibilite(userId1, userId2);

        assertThat(response.get(0).couleurHex()).isEqualTo("#27AE60"); // ECHANGE vert
        assertThat(response.get(1).couleurHex()).isEqualTo("#F39C12"); // CHEVAUCHEMENT orange
        assertThat(response.get(2).couleurHex()).isEqualTo("#3498DB"); // COLOCATION bleu
    }

    // ─── Erreur si user1 sans profil ──────────────────────────────────────────

    @Test
    void shouldThrowWhenUser1ProfileNotFound() {
        when(profileRepository.findByUserId(userId1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendrierService.getCalendrierCompatibilite(userId1, userId2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId1.toString());
    }

    // ─── Erreur si user2 sans profil ──────────────────────────────────────────

    @Test
    void shouldThrowWhenUser2ProfileNotFound() {
        when(profileRepository.findByUserId(userId1)).thenReturn(Optional.of(profileA));
        when(profileRepository.findByUserId(userId2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendrierService.getCalendrierCompatibilite(userId1, userId2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId2.toString());
    }

    // ─── Liste vide si aucune semaine commune ─────────────────────────────────

    @Test
    void shouldReturnEmptyListWhenNoCommonWeeks() {
        when(profileRepository.findByUserId(userId1)).thenReturn(Optional.of(profileA));
        when(profileRepository.findByUserId(userId2)).thenReturn(Optional.of(profileB));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());

        MatchingResult result = new MatchingResult(0.0, null,
                false, null, List.of(), 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, "Aucune semaine");

        when(calculator.calculate(any(), any(), any(), any())).thenReturn(result);

        List<SemaineCompatibilite> response = calendrierService.getCalendrierCompatibilite(userId1, userId2);

        assertThat(response).isEmpty();
    }

    // ─── Override — succès ────────────────────────────────────────────────────

    @Test
    void shouldOverrideSemaineFuture() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(userAlice));
        when(profileRepository.findById(profileAlice.getId())).thenReturn(Optional.of(profileAlice));
        when(scheduleRepository.findByProfileIdAndSemaine(profileAlice.getId(), NEXT_MONDAY))
                .thenReturn(Optional.of(schedule));

        AlternanceSchedule saved = AlternanceSchedule.builder()
                .id(schedule.getId()).profile(profileAlice).semaine(NEXT_MONDAY)
                .label("B").isOverridden(true).overrideReason("conges").build();
        when(scheduleRepository.save(any())).thenReturn(saved);

        AlternanceScheduleResponse response = calendrierService.overrideSemaine(
                "alice@studup.fr", profileAlice.getId(), NEXT_MONDAY,
                new OverrideScheduleRequest("B", "conges"));

        assertThat(response.label()).isEqualTo("B");
        assertThat(response.isOverridden()).isTrue();
        assertThat(response.overrideReason()).isEqualTo("conges");
    }

    // ─── Override — semaine passée interdite ──────────────────────────────────

    @Test
    void shouldRejectPastSemaine() {
        LocalDate pastMonday = LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY);
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(userAlice));
        when(profileRepository.findById(profileAlice.getId())).thenReturn(Optional.of(profileAlice));

        assertThatThrownBy(() -> calendrierService.overrideSemaine(
                "alice@studup.fr", profileAlice.getId(), pastMonday,
                new OverrideScheduleRequest("B", "conges")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passée");
    }

    // ─── Override — ownership check ───────────────────────────────────────────

    @Test
    void shouldRejectWhenNotOwner() {
        User bob = User.builder().id(UUID.randomUUID()).email("bob@studup.fr")
                .firstName("B").lastName("B").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("bob@studup.fr")).thenReturn(Optional.of(bob));
        when(profileRepository.findById(profileAlice.getId())).thenReturn(Optional.of(profileAlice));

        assertThatThrownBy(() -> calendrierService.overrideSemaine(
                "bob@studup.fr", profileAlice.getId(), NEXT_MONDAY,
                new OverrideScheduleRequest("B", "conges")))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─── Override — semaine introuvable ───────────────────────────────────────

    @Test
    void shouldThrowWhenSemaineNotFound() {
        LocalDate unknownDate = NEXT_MONDAY.plusWeeks(50);
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(userAlice));
        when(profileRepository.findById(profileAlice.getId())).thenReturn(Optional.of(profileAlice));
        when(scheduleRepository.findByProfileIdAndSemaine(profileAlice.getId(), unknownDate))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendrierService.overrideSemaine(
                "alice@studup.fr", profileAlice.getId(), unknownDate,
                new OverrideScheduleRequest("B", "conges")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Override — invalidation cache Redis ──────────────────────────────────

    @Test
    void shouldInvalidateRedisCache() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(userAlice));
        when(profileRepository.findById(profileAlice.getId())).thenReturn(Optional.of(profileAlice));
        when(scheduleRepository.findByProfileIdAndSemaine(profileAlice.getId(), NEXT_MONDAY))
                .thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(any())).thenReturn(schedule);
        when(redisTemplate.keys(any()))
                .thenReturn(Set.of("matching:" + UUID.randomUUID() + ":" + profileAlice.getId()));

        calendrierService.overrideSemaine("alice@studup.fr", profileAlice.getId(), NEXT_MONDAY,
                new OverrideScheduleRequest("B", "rattrapage"));

        verify(redisTemplate).keys("matching:*" + profileAlice.getId() + "*");
        verify(redisTemplate).delete(any(Set.class));
    }
}
