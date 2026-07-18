package com.studup.backend.scheduler;

import com.studup.backend.model.enums.AccordStatut;
import com.studup.backend.repository.AccordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccordExpirationJobTest {

    @Mock
    private AccordRepository accordRepository;

    @InjectMocks
    private AccordExpirationJob job;

    // ─── shouldExpireOldPendingAccords ────────────────────────────────────────

    @Test
    void shouldExpireOldPendingAccords() {
        when(accordRepository.expireAccordsEnAttente(any(), eq(AccordStatut.ANNULE.name())))
                .thenReturn(3);

        job.expireOldPendingAccords();

        // Vérifie que le repository est appelé avec le statut ANNULE
        verify(accordRepository).expireAccordsEnAttente(any(OffsetDateTime.class), eq(AccordStatut.ANNULE.name()));
    }

    // ─── shouldHandleZeroExpiredAccords ───────────────────────────────────────

    @Test
    void shouldHandleZeroExpiredAccords() {
        when(accordRepository.expireAccordsEnAttente(any(), any()))
                .thenReturn(0);

        // Aucune exception si aucun accord à expirer
        job.expireOldPendingAccords();

        verify(accordRepository).expireAccordsEnAttente(any(OffsetDateTime.class), eq(AccordStatut.ANNULE.name()));
    }
}
