package com.example.orderservice.dto;

import com.example.orderservice.model.Cart;
import com.example.orderservice.model.CartItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(String id, List<CartItem> items, BigDecimal subtotal, Instant updatedAt) {
    public static CartResponse from(Cart cart) {
        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> item.unitPriceSnapshot().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), List.copyOf(cart.getItems()), subtotal, cart.getUpdatedAt());
    }
}
