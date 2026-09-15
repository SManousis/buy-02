package com.example.orderservice.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(@Min(0) int quantity) {}
