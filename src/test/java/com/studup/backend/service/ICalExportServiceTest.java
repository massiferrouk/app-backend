package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.response.IcalTokenResponse;
import com.studup.backend.model.entity.AlternanceSchedule;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.IcalToken;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.RythmeAlternance;
import com.studup.backend.model.enums.UserRole;
import com.studup.backend.repository.AccordRepository;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.IcalTokenRepository;
import com.studup.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ICalExportServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private IcalTokenRepository icalTokenRepository;
    @Mock private AlternantProfileRepository alternantProfileRepository;
    @Mock private AlternanceScheduleRepository alternanceScheduleRepository;
    @Mock private AccordRepository accordRepository;

    @InjectMocks private ICalExportService service;

    private User alice;
    private AlternantProfile profile;

    @BeforeEach
    void setUp() {
        // Injection manuelle de baseUrl (non injecté par Mockito)
        service = new ICalExportService(
                userRepository, icalTokenRepository,
                alternantProfileRepository, alternanceScheduleRepository,
                accordRepository, "http://localhost:8080");

        alice = User.builder()
                .id(UUID.randomUUID()).email("alice@studup.fr")
                .firstName("Alice").lastName("A").role(UserRole.ALTERNANT)
                .isVerified(true).isActive(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();

        profile = AlternantProfile.builder()
                .id(UUID.randomUUID()).user(alice)
                .villeA("Paris").villeB("Lyon")
                .rythme(RythmeAlternance.SEMAINE_3_1)
                .dateDebut(LocalDate.now()).dateFin(LocalDate.now().plusYears(1))
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
    }

    // ─── génération du fichier .ics ──────────────────────────────────────────

    @Test
    void shouldGenerateValidICalFormat() {
        AlternanceSchedule semaine = AlternanceSchedule.builder()
                .id(UUID.randomUUID()).profile(profile)
                .semaine(LocalDate.of(2026, 9, 7))
                .label("A").isOverridden(false)
                .createdAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(alternantProfileRepository.findByUserId(alice.getId())).thenReturn(Optional.of(profile));
        when(alternanceScheduleRepository.findByProfileIdOrderBySemaineAsc(profile.getId())).thenReturn(List.of(semaine));
        when(accordRepository.findActiveAccordsForUser(alice.getId())).thenReturn(List.of());

        String ical = service.generateIcal("alice@studup.fr");

        assertThat(ical).contains("BEGIN:VCALENDAR");
        assertThat(ical).contains("END:VCALENDAR");
        assertThat(ical).contains("BEGIN:VEVENT");
        assertThat(ical).contains("END:VEVENT");
        assertThat(ical).contains("Paris (École)");
        assertThat(ical).contains("DTSTART;VALUE=DATE:20260907");
    }

    // ─── token invalide → exception ──────────────────────────────────────────

    @Test
    void shouldThrowWhenTokenInvalid() {
        when(icalTokenRepository.findByTokenAndIsActiveTrue("mauvais-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateIcalByToken("mauvais-token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── token valide → génère le .ics et met à jour lastUsed ────────────────

    @Test
    void shouldGenerateIcalByValidToken() {
        IcalToken token = IcalToken.builder()
                .id(UUID.randomUUID()).userId(alice.getId())
                .token("abc123").isActive(true)
                .createdAt(OffsetDateTime.now()).build();

        when(icalTokenRepository.findByTokenAndIsActiveTrue("abc123")).thenReturn(Optional.of(token));
        when(icalTokenRepository.save(any())).thenReturn(token);
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(alternantProfileRepository.findByUserId(alice.getId())).thenReturn(Optional.empty());
        when(accordRepository.findActiveAccordsForUser(alice.getId())).thenReturn(List.of());

        String ical = service.generateIcalByToken("abc123");

        assertThat(ical).contains("BEGIN:VCALENDAR");
        verify(icalTokenRepository).save(argThat(t -> t.getLastUsed() != null));
    }

    // ─── création token si aucun existant ────────────────────────────────────

    @Test
    void shouldCreateTokenWhenNoneExists() {
        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(icalTokenRepository.findByUserId(alice.getId())).thenReturn(Optional.empty());
        when(icalTokenRepository.save(any())).thenAnswer(inv -> {
            IcalToken t = inv.getArgument(0);
            t.setToken(t.getToken() != null ? t.getToken() : "generated-token");
            return t;
        });

        IcalTokenResponse response = service.getOrCreateToken("alice@studup.fr");

        assertThat(response.token()).isNotNull();
        assertThat(response.subscribeUrl()).contains("/api/v1/calendrier/subscribe/");
        verify(icalTokenRepository).save(any());
    }

    // ─── régénération : l'ancien token est révoqué ───────────────────────────

    @Test
    void shouldRevokeOldTokenOnRegenerate() {
        IcalToken ancien = IcalToken.builder()
                .id(UUID.randomUUID()).userId(alice.getId())
                .token("ancien-token").isActive(true)
                .createdAt(OffsetDateTime.now()).build();

        when(userRepository.findByEmail("alice@studup.fr")).thenReturn(Optional.of(alice));
        when(icalTokenRepository.findByUserId(alice.getId())).thenReturn(Optional.of(ancien));
        when(icalTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.regenerateToken("alice@studup.fr");

        ArgumentCaptor<IcalToken> captor = ArgumentCaptor.forClass(IcalToken.class);
        verify(icalTokenRepository, times(2)).save(captor.capture());
        // Le premier save est la révocation de l'ancien
        assertThat(captor.getAllValues().get(0).getIsActive()).isFalse();
    }
}
