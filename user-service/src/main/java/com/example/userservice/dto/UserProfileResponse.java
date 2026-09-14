package com.example.userservice.dto;

import com.example.userservice.model.User;

import java.time.Instant;

/** Safe user view — password is never included. */
public record UserProfileResponse(
        String id,
        String username,
        String email,
        String role,
        String avatarMediaId,
        Instant createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getAvatarMediaId(),
                user.getCreatedAt()
        );
    }
}
