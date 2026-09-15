package com.example.orderservice.dto;

public record UnavailableItem(
        String productId,
        String name,
        String reason
) {}
