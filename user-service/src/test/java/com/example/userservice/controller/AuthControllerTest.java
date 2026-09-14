package com.example.userservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.userservice.dto.AuthResponse;
import com.example.userservice.exception.ConflictException;
import com.example.userservice.exception.GlobalExceptionHandler;
import com.example.userservice.service.UserService;

class AuthControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = org.mockito.Mockito.mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerReturnsCreatedAuthenticationResponse() throws Exception {
        when(userService.register(any())).thenReturn(authentication());

        mockMvc.perform(post("/auth/register")
                .contentType("application/json")
                .content("""
                        {"username":"alice","email":"alice@example.com","password":"password123","role":"SELLER"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").value("seller-1"))
                .andExpect(jsonPath("$.role").value("SELLER"));

        verify(userService).register(any());
    }

    @Test
    void registerRejectsAnInvalidRequestBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType("application/json")
                .content("""
                        {"username":"ab","email":"invalid-email","password":"short","role":null}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.username").value("Username must be 3–50 characters"))
                .andExpect(jsonPath("$.details.email").value("Email must be valid"))
                .andExpect(jsonPath("$.details.password").value("Password must be at least 8 characters"))
                .andExpect(jsonPath("$.details.role").value("Role is required (CLIENT or SELLER)"));

        verify(userService, never()).register(any());
    }

    @Test
    void registerMapsExistingIdentityToConflict() throws Exception {
        when(userService.register(any())).thenThrow(new ConflictException("Email already in use"));

        mockMvc.perform(post("/auth/register")
                .contentType("application/json")
                .content("""
                        {"username":"alice","email":"alice@example.com","password":"password123","role":"CLIENT"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void loginRejectsInvalidInputBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content("""
                        {"email":"invalid-email","password":""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.email").value("Email must be valid"))
                .andExpect(jsonPath("$.details.password").value("Password is required"));

        verify(userService, never()).login(any());
    }

    @Test
    void loginMapsIncorrectCredentialsToUnauthorized() throws Exception {
        when(userService.login(any())).thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content("""
                        {"email":"alice@example.com","password":"wrong-password"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    private AuthResponse authentication() {
        return new AuthResponse("jwt-token", "seller-1", "alice", "SELLER");
    }
}
