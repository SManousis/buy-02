package com.example.orderservice.dto;

import com.example.orderservice.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @Valid @NotNull ShippingAddressRequest shippingAddress,
        @NotNull PaymentMethod paymentMethod
) {}
