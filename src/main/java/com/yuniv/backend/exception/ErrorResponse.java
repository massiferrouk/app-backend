package com.yuniv.backend.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp,
        String path,
        List<String> details
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, OffsetDateTime.now(), path, List.of());
    }

    public static ErrorResponse of(String code, String message, String path, List<String> details) {
        return new ErrorResponse(code, message, OffsetDateTime.now(), path, details);
    }
}
