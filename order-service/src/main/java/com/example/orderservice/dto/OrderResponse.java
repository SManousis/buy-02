package com.example.orderservice.dto;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.PaymentMethod;
import com.example.orderservice.model.PaymentStatus;
import com.example.orderservice.model.ShippingAddress;
import com.example.orderservice.model.StatusHistoryEntry;
import com.example.orderservice.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String checkoutGroupId,
        String buyerId,
        String sellerId,
        List<OrderItem> items,
        BigDecimal subtotal,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        List<StatusHistoryEntry> statusHistory,
        ShippingAddress shippingAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(), order.getCheckoutGroupId(), order.getBuyerId(), order.getSellerId(),
                order.getItems(), order.getSubtotal(), order.getStatus(), order.getPaymentMethod(),
                order.getPaymentStatus(), order.getStatusHistory(), order.getShippingAddress(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
