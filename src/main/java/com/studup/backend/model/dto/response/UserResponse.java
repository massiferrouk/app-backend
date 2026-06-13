package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.User;
import com.studup.backend.model.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        Boolean isVerified,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getIsVerified(),
                user.getCreatedAt()
        );
    }
}
