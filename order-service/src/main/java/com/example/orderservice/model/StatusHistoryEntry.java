package com.example.orderservice.model;

import java.time.Instant;

public record StatusHistoryEntry(
        OrderStatus status,
        Instant changedAt,
        String changedBy
) {}
