package com.example.orderservice.kafka;

import com.example.orderservice.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(
        String eventType,
        String orderId,
        String checkoutGroupId,
        String buyerId,
        String sellerId,
        BigDecimal subtotal,
        OrderStatus status,
        Instant occurredAt
) {}
