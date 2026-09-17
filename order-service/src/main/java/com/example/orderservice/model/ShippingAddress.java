package com.example.orderservice.model;

public record ShippingAddress(
        String line1,
        String city,
        String postalCode,
        String country
) {}
