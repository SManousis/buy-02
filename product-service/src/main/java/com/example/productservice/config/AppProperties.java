package com.example.productservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid JwtProperties jwt,
        @Valid CorsProperties cors,
        @Valid KafkaProperties kafka,
        @Valid MediaProperties media
) {
    public record JwtProperties(
            @NotBlank(message = "app.jwt.secret must not be blank")
            @Size(min = 32, message = "app.jwt.secret must be at least 32 characters")
            String secret,
            @Min(value = 60_000, message = "app.jwt.expiration-ms must be at least 60 seconds")
            long expirationMs,
            @NotBlank String issuer,
            @NotBlank String audience
    ) {}

    public record CorsProperties(List<@NotBlank String> allowedOrigins) {}

    public record KafkaProperties(
            @Valid Topics topics
    ) {
        public record Topics(
                @NotBlank String productCreated,
                @NotBlank String productUpdated,
                @NotBlank String productDeleted,
                @NotBlank String imageDeleted
        ) {}
    }

    public record MediaProperties(
            @NotBlank String baseUrl
    ) {}
}
