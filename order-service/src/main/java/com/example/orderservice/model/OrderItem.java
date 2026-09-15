package com.example.orderservice.model;

import java.math.BigDecimal;

public record OrderItem(
        String productId,
        String name,
        BigDecimal unitPrice,
        int quantity,
        String imageId
) {}
