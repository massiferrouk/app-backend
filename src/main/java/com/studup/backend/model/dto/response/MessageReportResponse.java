package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.MessageReport;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageReportResponse(
        UUID id,
        UUID messageId,
        UUID reporterId,
        String motif,
        OffsetDateTime createdAt
) {
    public static MessageReportResponse from(MessageReport report) {
        return new MessageReportResponse(
                report.getId(),
                report.getMessageId(),
                report.getReporterId(),
                report.getMotif(),
                report.getCreatedAt()
        );
    }
}
