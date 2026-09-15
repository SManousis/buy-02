package com.example.orderservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid JwtProperties jwt, @Valid CorsProperties cors,
                            @Valid ProductProperties product) {
    public record JwtProperties(
            @NotBlank @Size(min = 32) String secret,
            @NotBlank String issuer,
            @NotBlank String audience) {}

    public record CorsProperties(List<@NotBlank String> allowedOrigins) {}

    public record ProductProperties(@NotBlank String baseUrl) {}
}
