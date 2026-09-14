package com.example.mediaservice.kafka;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.time.Instant;

// Mirrors product-service ProductEvent — only fields needed for image cleanup
public record ProductDeletedEvent(
        String eventType,
        String productId,
        String sellerId,
        @JsonAlias("imageUrls") List<String> imageIds,
        Instant occurredAt
) {}
