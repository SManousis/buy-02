package com.example.orderservice.dto;

import java.util.List;

public record ReorderResponse(
        CartResponse cart,
        List<UnavailableItem> unavailableItems
) {}
