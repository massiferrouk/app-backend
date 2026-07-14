package com.studup.backend.service;

import com.studup.backend.algorithm.CompatibilityCalculator;
import com.studup.backend.algorithm.MatchingResult;
import com.studup.backend.model.entity.AlternantProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.AccordType;
import com.studup.backend.model.enums.NotificationType;
import com.studup.backend.repository.AlternanceScheduleRepository;
import com.studup.backend.repository.AlternantProfileRepository;
import com.studup.backend.repository.MatchNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchNotificationServiceTest {

    @Mock private AlternantProfileRepository profileRepository;
    @Mock private AlternanceScheduleRepository scheduleRepository;
    @Mock private CompatibilityCalculator calculator;
    @Mock private MatchNotificationRepository matchNotificationRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private MatchNotificationService service;

    private UUID meUserId;
    private UUID otherUserId;
    private AlternantProfile me;
    private AlternantProfile candidate;

    @BeforeEach
    void setUp() {
        meUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        User meUser = User.builder().id(meUserId).firstName("Alice").build();
        User otherUser = User.builder().id(otherUserId).firstName("Bob").build();

        me = AlternantProfile.builder().id(UUID.randomUUID()).user(meUser)
                .villeA("Paris").villeB("Lyon").build();
        candidate = AlternantProfile.builder().id(UUID.randomUUID()).user(otherUser)
                .villeA("Lyon").villeB("Paris").build();
    }

    @Test
    void shouldNotifyBothAlternantsOnNewMatch() {
        MatchingResult result = mock(MatchingResult.class);
        when(result.typePropose()).thenReturn(AccordType.ECHANGE_TOTAL);

        when(profileRepository.findByUserId(meUserId)).thenReturn(Optional.of(me));
        when(profileRepository.findCandidatesWithSharedCity(any(), any(), any()))
                .thenReturn(List.of(candidate));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());
        when(calculator.calculate(any(), any(), any(), any())).thenReturn(result);
        when(matchNotificationRepository.existsByUserAIdAndUserBId(any(), any())).thenReturn(false);

        service.notifyNewMatches(meUserId);

        // Paire enregistrée + les DEUX alternants notifiés
        verify(matchNotificationRepository).save(any());
        verify(notificationService).notify(eq(meUserId), eq(NotificationType.NOUVEAU_MATCH), any(), any());
        verify(notificationService).notify(eq(otherUserId), eq(NotificationType.NOUVEAU_MATCH), any(), any());
    }

    @Test
    void shouldNotRenotifyAlreadyNotifiedPair() {
        MatchingResult result = mock(MatchingResult.class);
        when(result.typePropose()).thenReturn(AccordType.ECHANGE_TOTAL);

        when(profileRepository.findByUserId(meUserId)).thenReturn(Optional.of(me));
        when(profileRepository.findCandidatesWithSharedCity(any(), any(), any()))
                .thenReturn(List.of(candidate));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());
        when(calculator.calculate(any(), any(), any(), any())).thenReturn(result);
        // Paire déjà notifiée
        when(matchNotificationRepository.existsByUserAIdAndUserBId(any(), any())).thenReturn(true);

        service.notifyNewMatches(meUserId);

        verify(matchNotificationRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldSkipCandidateWithoutMatch() {
        MatchingResult result = mock(MatchingResult.class);
        when(result.typePropose()).thenReturn(null); // aucune compatibilité

        when(profileRepository.findByUserId(meUserId)).thenReturn(Optional.of(me));
        when(profileRepository.findCandidatesWithSharedCity(any(), any(), any()))
                .thenReturn(List.of(candidate));
        when(scheduleRepository.findByProfileIdOrderBySemaineAsc(any())).thenReturn(List.of());
        when(calculator.calculate(any(), any(), any(), any())).thenReturn(result);

        service.notifyNewMatches(meUserId);

        verifyNoInteractions(notificationService);
        verify(matchNotificationRepository, never()).save(any());
    }
}
