package com.ekp.auth.dto;

import com.ekp.user.User;

import java.time.Instant;
import java.util.UUID;

/** Client-safe view of a user. Never exposes the password hash. */
public record UserResponse(
        UUID id,
        String email,
        String role,
        boolean enabled,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
