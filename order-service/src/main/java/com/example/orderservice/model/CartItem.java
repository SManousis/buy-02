package com.example.orderservice.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CartItem(String productId, String sellerId, String productName,
                       int quantity, BigDecimal unitPriceSnapshot, Instant addedAt) {}
