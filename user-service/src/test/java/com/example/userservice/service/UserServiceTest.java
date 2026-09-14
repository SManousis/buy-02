package com.example.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.dto.UpdateProfileRequest;
import com.example.userservice.client.MediaOwnershipClient;
import com.example.userservice.exception.ConflictException;
import com.example.userservice.exception.InvalidAvatarMediaException;
import com.example.userservice.exception.NotFoundException;
import com.example.userservice.model.User;
import com.example.userservice.model.UserRole;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JwtService;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private MediaOwnershipClient mediaOwnershipClient;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder,
                jwtService,
                mediaOwnershipClient
        );
    }

    private User seller() {
        return User.builder()
                .id("seller-1")
                .username("alice")
                .email("alice@example.com")
                .role(UserRole.SELLER)
                .build();
    }

    private User client() {
        return User.builder()
                .id("client-1")
                .username("bob")
                .email("bob@example.com")
                .role(UserRole.CLIENT)
                .build();
    }

    @Test
    void registerNormalizesIdentityBeforePersistingAndReturnsAuthentication() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("Alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(jwtService.generateToken(org.mockito.ArgumentMatchers.any(User.class))).thenReturn("jwt-token");

        var response = userService.register(new RegisterRequest(
                "  Alice  ", "  ALICE@EXAMPLE.COM  ", "password123", UserRole.SELLER));

        org.mockito.ArgumentCaptor<User> savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue())
                .extracting(User::getUsername, User::getEmail, User::getPassword, User::getRole)
                .containsExactly("Alice", "alice@example.com", "encoded-password", UserRole.SELLER);
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.username()).isEqualTo("Alice");
        assertThat(response.role()).isEqualTo("SELLER");
    }

    @Test
    void registerRejectsAnExistingEmailBeforeEncodingOrSaving() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(new RegisterRequest(
                "alice", "alice@example.com", "password123", UserRole.CLIENT)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already in use");

        verify(userRepository, never()).existsByUsername(org.mockito.ArgumentMatchers.anyString());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registerRejectsAnExistingUsernameBeforeEncodingOrSaving() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(new RegisterRequest(
                "alice", "alice@example.com", "password123", UserRole.CLIENT)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username already taken");

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginNormalizesEmailAndReturnsAuthenticationForValidCredentials() {
        User user = seller();
        user.setPassword("encoded-password");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        var response = userService.login(new LoginRequest("  ALICE@EXAMPLE.COM  ", "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.userId()).isEqualTo("seller-1");
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo("SELLER");
    }

    @Test
    void loginRejectsAnIncorrectPasswordWithoutGeneratingAToken() {
        User user = seller();
        user.setPassword("encoded-password");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(new LoginRequest("alice@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(jwtService, never()).generateToken(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void profileUpdateRejectsAUsernameHeldByAnotherUserWithoutPersistingChanges() {
        User seller = seller();
        User existingUser = User.builder().id("seller-2").username("eve").build();
        when(userRepository.findById("seller-1")).thenReturn(Optional.of(seller));
        when(userRepository.findByUsername("eve")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> userService.updateProfile(
                "seller-1", "token", new UpdateProfileRequest("eve", null, false)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username already taken");

        assertThat(seller.getUsername()).isEqualTo("alice");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getProfileRejectsAnUnknownUser() {
        when(userRepository.findById("missing-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile("missing-user"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void sellerCanAssignOwnedAvatar() {
        User seller = seller();
        when(userRepository.findById("seller-1"))
                .thenReturn(Optional.of(seller));
        when(userRepository.save(seller)).thenReturn(seller);

        var request = new UpdateProfileRequest(
                null,
                "media-1",
                false
        );

        var response = userService.updateProfile(
                "seller-1",
                "token",
                request
        );

        verify(mediaOwnershipClient).verifyOwnedImage(
                "seller-1",
                "media-1",
                "token"
        );
        verify(userRepository).save(seller);

        assertThat(seller.getAvatarMediaId()).isEqualTo("media-1");
        assertThat(response.avatarMediaId()).isEqualTo("media-1");
    }

    @Test
    void clientCannotAssignAvatar() {
        User client = client();
        when(userRepository.findById("client-1"))
                .thenReturn(Optional.of(client));

        var request = new UpdateProfileRequest(
                null,
                "media-1",
                false
        );

        assertThatThrownBy(() -> userService.updateProfile(
                "client-1",
                "token",
                request
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("sellers");

        verify(mediaOwnershipClient, never())
                .verifyOwnedImage(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(userRepository, never()).save(
                org.mockito.ArgumentMatchers.any()
        );
    }
    @Test
    void invalidAvatarIsNotPersisted() {
        User seller = seller();
        when(userRepository.findById("seller-1"))
                .thenReturn(Optional.of(seller));

        var request = new UpdateProfileRequest(
                null,
                "foreign-media",
                false
        );

        org.mockito.Mockito.doThrow(
                new InvalidAvatarMediaException(
                        "Invalid avatar media reference"
                )
        ).when(mediaOwnershipClient).verifyOwnedImage(
                "seller-1",
                "foreign-media",
                "token"
        );

        assertThatThrownBy(() -> userService.updateProfile(
                "seller-1",
                "token",
                request
        )).isInstanceOf(InvalidAvatarMediaException.class);

        assertThat(seller.getAvatarMediaId()).isNull();
        verify(userRepository, never()).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void sellerCanRemoveAvatar() {
        User seller = seller();
        seller.setAvatarMediaId("media-1");

        when(userRepository.findById("seller-1"))
                .thenReturn(Optional.of(seller));
        when(userRepository.save(seller)).thenReturn(seller);

        var request = new UpdateProfileRequest(
                null,
                null,
                true
        );

        var response = userService.updateProfile(
                "seller-1",
                "token",
                request
        );

        assertThat(seller.getAvatarMediaId()).isNull();
        assertThat(response.avatarMediaId()).isNull();

        verify(mediaOwnershipClient, never())
                .verifyOwnedImage(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );
        verify(mediaOwnershipClient, never()).deleteOwnedImage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );

        verify(userRepository).save(seller);
    }

    @Test
    void cannotAssignAndRemoveAvatarTogether() {
        User seller = seller();
        when(userRepository.findById("seller-1"))
                .thenReturn(Optional.of(seller));

        var request = new UpdateProfileRequest(
                null,
                "media-1",
                true
        );

        assertThatThrownBy(() -> userService.updateProfile(
                "seller-1",
                "token",
                request
        ))
                .isInstanceOf(InvalidAvatarMediaException.class)
                .hasMessageContaining("same request");

        verify(mediaOwnershipClient, never())
                .verifyOwnedImage(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(userRepository, never()).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void unchangedAvatarIsNotSavedAgain() {
        User seller = seller();
        seller.setAvatarMediaId("media-1");

        when(userRepository.findById("seller-1"))
                .thenReturn(Optional.of(seller));

        var request = new UpdateProfileRequest(
                null,
                "media-1",
                false
        );

        userService.updateProfile("seller-1", "token", request);

        verify(mediaOwnershipClient).verifyOwnedImage(
                "seller-1",
                "media-1",
                "token"
        );

        verify(userRepository, never()).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void resubmittingExistingAvatarDoesNotDeleteItWhenUsernamePersistenceFails() {
        User seller = seller();
        seller.setAvatarMediaId("media-1");
        when(userRepository.findById("seller-1")).thenReturn(Optional.of(seller));
        when(userRepository.findByUsername("alice-updated")).thenReturn(Optional.empty());
        when(userRepository.save(seller)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> userService.updateProfile(
                "seller-1", "token", new UpdateProfileRequest("alice-updated", "media-1", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(mediaOwnershipClient, never()).deleteOwnedImage("media-1", "token");
    }

    @Test
    void cleansUpNewAvatarWhenProfilePersistenceFails() {
        User seller = seller();
        when(userRepository.findById("seller-1")).thenReturn(Optional.of(seller));
        when(userRepository.save(seller)).thenThrow(new IllegalStateException("database unavailable"));

        var request = new UpdateProfileRequest(null, "media-1", false);

        assertThatThrownBy(() -> userService.updateProfile("seller-1", "token", request))
                .isInstanceOf(IllegalStateException.class);

        verify(mediaOwnershipClient).deleteOwnedImage("media-1", "token");
    }

    @Test
    void preservesPersistenceFailureWhenAvatarCleanupFails() {
        User seller = seller();
        when(userRepository.findById("seller-1")).thenReturn(Optional.of(seller));
        IllegalStateException persistenceFailure = new IllegalStateException("database unavailable");
        when(userRepository.save(seller)).thenThrow(persistenceFailure);
        org.mockito.Mockito.doThrow(new IllegalStateException("media unavailable"))
                .when(mediaOwnershipClient).deleteOwnedImage("media-1", "token");

        assertThatThrownBy(() -> userService.updateProfile(
                "seller-1", "token", new UpdateProfileRequest(null, "media-1", false)))
                .isSameAs(persistenceFailure)
                .satisfies(exception -> assertThat(exception.getSuppressed()).hasSize(1));
    }

    @Test
    void replacingAvatarKeepsThePreviousMediaAvailable() {
        User seller = seller();
        seller.setAvatarMediaId("media-1");
        when(userRepository.findById("seller-1")).thenReturn(Optional.of(seller));
        when(userRepository.save(seller)).thenReturn(seller);

        userService.updateProfile("seller-1", "token",
                new UpdateProfileRequest(null, "media-2", false));

        assertThat(seller.getAvatarMediaId()).isEqualTo("media-2");
        verify(mediaOwnershipClient, never()).deleteOwnedImage("media-1", "token");
    }
}
