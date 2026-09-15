package com.example.orderservice.client;

import java.math.BigDecimal;

public record ProductSnapshot(String id, String sellerId, String name, BigDecimal price, Integer stock) {}
