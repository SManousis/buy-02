package com.example.orderservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ShippingAddressRequest(
        @NotBlank String line1,
        @NotBlank String city,
        @NotBlank String postalCode,
        @NotBlank String country
) {}
