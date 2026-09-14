package com.example.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Typed, validated binding of all `app.*` properties.
 *
 * Fail-fast on startup: if JWT_SECRET is blank or expiration is negative,
 * the application refuses to start rather than silently misbehaving.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        @Valid JwtProperties jwt,
        @Valid CorsProperties cors,
        @Valid MediaProperties media

) {
    public record JwtProperties(

            @NotBlank(message = "app.jwt.secret must not be blank")
            @Size(min = 32, message = "app.jwt.secret must be at least 32 characters")
            String secret,

            @Min(value = 60_000, message = "app.jwt.expiration-ms must be at least 60 seconds")
            long expirationMs,

            @NotBlank(message = "app.jwt.issuer must not be blank")
            String issuer,

            @NotBlank(message = "app.jwt.audience must not be blank")
            String audience
    ) {}

    public record CorsProperties(List<@NotBlank String> allowedOrigins) {}

    public record MediaProperties(
            @NotBlank(message = "app.media.service-name must not be blank")
            String serviceName
    ) {}
}
