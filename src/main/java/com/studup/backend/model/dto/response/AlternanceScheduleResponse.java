package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.AlternanceSchedule;

import java.time.LocalDate;
import java.util.UUID;

public record AlternanceScheduleResponse(
        UUID id,
        LocalDate semaine,
        String label,
        Boolean isOverridden,
        String overrideReason
) {
    public static AlternanceScheduleResponse from(AlternanceSchedule s) {
        return new AlternanceScheduleResponse(
                s.getId(),
                s.getSemaine(),
                s.getLabel(),
                s.getIsOverridden(),
                s.getOverrideReason()
        );
    }
}
