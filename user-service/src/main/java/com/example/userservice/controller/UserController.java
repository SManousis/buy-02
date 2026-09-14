package com.example.userservice.controller;

import com.example.userservice.dto.UpdateProfileRequest;
import com.example.userservice.dto.UserProfileResponse;
import com.example.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /me
     * Requires: Bearer JWT
     * Returns the authenticated user's profile (no password).
     */
    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getProfile(jwt.getSubject()));
    }

    /**
     * PUT /me
     * Requires: Bearer JWT
     * Body: { "username"?, "avatarMediaId"?, "removeAvatar"? }  (all fields optional)
     * Returns updated profile.
     */
    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(userService.updateProfile(jwt.getSubject(), jwt.getTokenValue(), request));
    }
}
