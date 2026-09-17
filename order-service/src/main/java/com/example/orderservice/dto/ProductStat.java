package com.example.orderservice.dto;

import java.math.BigDecimal;

/**
 * One product's totals across a user's non-cancelled orders. {@code name} and
 * {@code imageId} come from the most recent order snapshot, so a product that
 * was renamed or deleted after purchase is still shown.
 */
public record ProductStat(
        String productId,
        String name,
        String imageId,
        int quantity,
        BigDecimal amount
) {}
