package com.example.userservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.example.userservice.dto.UserProfileResponse;
import com.example.userservice.exception.GlobalExceptionHandler;
import com.example.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = org.mockito.Mockito.mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("seller-1"));
    }

    @Test
    void getProfileUsesJwtSubject() throws Exception {
        when(userService.getProfile("seller-1")).thenReturn(profile());

        mockMvc.perform(get("/me"))
                .andExpect(status().isOk());

        verify(userService).getProfile("seller-1");
    }

    @Test
    void updateProfileForwardsBearerToken() throws Exception {
        when(userService.updateProfile(any(), any(), any())).thenReturn(profile());

        mockMvc.perform(put("/me")
                .contentType("application/json")
                .content("{\"avatarMediaId\":\"media-1\"}"))
                .andExpect(status().isOk());

        verify(userService).updateProfile(
                org.mockito.ArgumentMatchers.eq("seller-1"),
                org.mockito.ArgumentMatchers.eq("token"),
                any()
        );
    }

    @Test
    void updateProfileRejectsBlankAvatarMediaIdBeforeCallingTheService() throws Exception {
        mockMvc.perform(put("/me")
                .contentType("application/json")
                .content("{\"avatarMediaId\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Validation failed"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.details.avatarMediaId")
                        .value("Avatar media ID must not be blank"));

        verify(userService, never()).updateProfile(any(), any(), any());
    }

    private UserProfileResponse profile() {
        return new UserProfileResponse(
                "seller-1", "alice", "alice@example.com", "SELLER", "media-1", Instant.now());
    }

    private JwtAuthenticationToken jwtAuthentication(String subject) {
        Jwt jwt = new Jwt(
                "token", Instant.now(), Instant.now().plusSeconds(300),
                java.util.Map.of("alg", "none"), java.util.Map.of("sub", subject));
        return new JwtAuthenticationToken(jwt);
    }
}
