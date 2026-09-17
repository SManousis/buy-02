package com.example.orderservice.client;

import java.math.BigDecimal;
import java.util.List;

public record ProductSnapshot(
        String id,
        String sellerId,
        String name,
        BigDecimal price,
        Integer stock,
        List<String> imageIds
) {
    public ProductSnapshot(String id, String sellerId, String name, BigDecimal price, Integer stock) {
        this(id, sellerId, name, price, stock, List.of());
    }
}
