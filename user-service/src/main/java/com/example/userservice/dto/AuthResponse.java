package com.example.userservice.dto;

/** Returned after successful register or login. */
public record AuthResponse(
        String token,
        String userId,
        String username,
        String role
) {}
