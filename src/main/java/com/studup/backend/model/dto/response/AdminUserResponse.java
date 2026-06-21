package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        boolean isVerified,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime deletedAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getIsVerified(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getDeletedAt()
        );
    }
}
