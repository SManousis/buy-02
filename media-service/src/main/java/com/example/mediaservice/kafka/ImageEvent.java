package com.example.mediaservice.kafka;

import java.time.Instant;

public record ImageEvent(
        String eventType,       // IMAGE_UPLOADED | IMAGE_DELETED
        String mediaId,         // used as Kafka partition key → ordering per image
        String sellerId,
        String originalFileName,
        String contentType,
        long   sizeBytes,
        Instant occurredAt
) {}
