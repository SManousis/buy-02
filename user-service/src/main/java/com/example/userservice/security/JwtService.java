package com.example.userservice.security;

import com.example.userservice.config.AppProperties;
import com.example.userservice.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Generates and validates JWTs.
 *
 * The JwtEncoder and JwtDecoder beans are defined in SecurityConfig,
 * making them the single source of truth for the symmetric key.
 * This class is purely responsible for the claim structure.
 */


@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties appProperties;

    /** Build and sign a JWT for the given user. */
    public String generateToken(User user) {
        Instant now = Instant.now();
        long expirationMs = appProperties.jwt().expirationMs();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(appProperties.jwt().issuer())
                .subject(user.getId())
                .issuedAt(now)
                .expiresAt(now.plusMillis(expirationMs))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .audience(List.of(appProperties.jwt().audience()))
                .build();

        JwsHeader header = JwsHeader.with(() -> "HS256").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Decode and verify a raw token string.
     * Throws {@link JwtException} if the token is expired, tampered, or malformed.
     */
    public Jwt validateToken(String token) {
        return decoder.decode(token);
    }
}
