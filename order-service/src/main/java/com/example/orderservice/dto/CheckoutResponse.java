package com.example.orderservice.dto;

import java.util.List;

public record CheckoutResponse(String checkoutGroupId, List<OrderResponse> orders) {}
