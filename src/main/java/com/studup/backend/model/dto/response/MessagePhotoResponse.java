package com.studup.backend.model.dto.response;

import java.util.List;

public record MessagePhotoResponse(
        String messageId,
        List<String> photoUrls
) {}
