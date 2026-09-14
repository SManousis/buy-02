package com.example.userservice.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(

        @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
        String username,

        /** Avatar Media ID set after uploading via Media Service */
        @Pattern(regexp = ".*\\S.*", message = "Avatar media ID must not be blank")
        String avatarMediaId,

        Boolean removeAvatar
) {}
