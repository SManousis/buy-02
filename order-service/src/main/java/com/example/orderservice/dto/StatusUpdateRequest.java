package com.example.orderservice.dto;

import com.example.orderservice.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull OrderStatus status
) {}
