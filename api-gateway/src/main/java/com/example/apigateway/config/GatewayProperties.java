package com.example.apigateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "app")
public record GatewayProperties(
        @Valid JwtProperties jwt,
        @Valid CorsProperties cors,
        @Valid UploadProperties upload
) {
    public record JwtProperties(
            @NotBlank(message = "app.jwt.secret must not be blank")
            @Size(min = 32, message = "app.jwt.secret must be at least 32 characters")
            String secret,
            @NotBlank(message = "app.jwt.issuer must not be blank")
            String issuer,
            @NotBlank(message = "app.jwt.audience must not be blank")
            String audience
    ) {}

    public record CorsProperties(
            @NotEmpty(message = "app.cors.allowed-origins must not be empty")
            List<@NotBlank String> allowedOrigins
    ) {}

    public record UploadProperties(
            @NotNull(message = "app.upload.max-request-size must not be null")
            DataSize maxRequestSize
    ) {}
}
