package com.example.productservice.kafka;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductEvent(
        String eventType,       // PRODUCT_CREATED | PRODUCT_UPDATED | PRODUCT_DELETED
        String productId,       // Kafka partition key → ordering per product
        String sellerId,
        String name,
        BigDecimal price,
        Integer stock,
        @JsonAlias("imageUrls") List<String> imageIds,
        Instant occurredAt
) {}
