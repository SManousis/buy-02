package com.example.productservice.dto;

import com.example.productservice.model.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        String id,
        String sellerId,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        List<String> imageIds,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageIds(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
