package com.example.userservice.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import com.example.userservice.dto.AuthResponse;
import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.dto.UpdateProfileRequest;
import com.example.userservice.dto.UserProfileResponse;
import com.example.userservice.client.MediaOwnershipClient;
import com.example.userservice.exception.ConflictException;
import com.example.userservice.exception.InvalidAvatarMediaException;
import com.example.userservice.exception.NotFoundException;
import com.example.userservice.model.User;
import com.example.userservice.model.UserRole;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JwtService;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core user domain logic.
 *
 * Flow:
 *   register → normalise → check uniqueness → hash password → save → JWT
 *   login    → normalise → find by email → verify password → JWT
 *   getMe    → find by subject (userId from JWT) → profile DTO
 *   updateMe → patch allowed fields → save → profile DTO
 *
 * @Transactional(readOnly=true) is the default for reads (faster in MongoDB replica sets).
 * Write methods explicitly set readOnly=false.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MediaOwnershipClient mediaOwnershipClient;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email    = request.email().toLowerCase().trim();
        String username = request.username().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already in use");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already taken");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        user = userRepository.save(user);
        log.info("User registered: id={} role={}", user.getId(), user.getRole());

        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        log.info("User logged in: id={}", user.getId());
        return toAuthResponse(user);
    }

    public UserProfileResponse getProfile(String userId) {
        return UserProfileResponse.from(findById(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(String userId, String bearerToken, UpdateProfileRequest request) {
        User user = findById(userId);
        boolean changed = false;
        String newlyAssignedAvatarMediaId = null;

        boolean removeAvatar = Boolean.TRUE.equals(request.removeAvatar());
        boolean assignAvatar = request.avatarMediaId() != null
                && !request.avatarMediaId().isBlank();

        if (removeAvatar && assignAvatar) {
            throw new InvalidAvatarMediaException(
                    "Cannot assign and remove an avatar in the same request");
        }

        if (request.username() != null && !request.username().isBlank()) {
            String newUsername = request.username().trim();
            if (!newUsername.equals(user.getUsername())) {
                userRepository.findByUsername(newUsername).ifPresent(existing -> {
                    if (!existing.getId().equals(userId)) {
                        throw new ConflictException("Username already taken");
                    }
                });
                user.setUsername(newUsername);
                changed = true;
            }
        }

        if (removeAvatar) {
            if (user.getAvatarMediaId() != null) {
                user.setAvatarMediaId(null);
                changed = true;
            }
        } else if (assignAvatar) {
            if (user.getRole() != UserRole.SELLER) {
                throw new AccessDeniedException("Only sellers can assign an avatar");
            }

            String mediaId = request.avatarMediaId().trim();
            mediaOwnershipClient.verifyOwnedImage(userId, mediaId, bearerToken);

            if (!mediaId.equals(user.getAvatarMediaId())) {
                user.setAvatarMediaId(mediaId);
                newlyAssignedAvatarMediaId = mediaId;
                changed = true;
            }
        }

        if (changed) {
            try {
                user = userRepository.save(user);
            } catch (RuntimeException persistenceException) {
                if (newlyAssignedAvatarMediaId != null) {
                    try {
                        mediaOwnershipClient.deleteOwnedImage(newlyAssignedAvatarMediaId, bearerToken);
                    } catch (RuntimeException cleanupException) {
                        persistenceException.addSuppressed(cleanupException);
                    }
                }
                throw persistenceException;
            }
            log.info("User profile updated: id={}", userId);
        }

        return UserProfileResponse.from(user);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }
}
