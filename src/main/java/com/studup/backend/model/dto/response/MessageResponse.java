package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.Message;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        Boolean isRead,
        OffsetDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getContent(),
                message.getIsRead(),
                message.getCreatedAt()
        );
    }
}
