package com.example.userservice.dto;

public record MediaMetadataResponse(
        String id,
        String sellerId,
        String contentType,
        long sizeBytes
) {}
