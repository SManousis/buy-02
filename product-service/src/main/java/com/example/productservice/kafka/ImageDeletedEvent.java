package com.example.productservice.kafka;

import java.time.Instant;

// Mirrors media-service ImageEvent — only fields needed for cleanup are declared
public record ImageDeletedEvent(
        String eventType,
        String mediaId,
        String sellerId,
        Instant occurredAt
) {}
