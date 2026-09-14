package com.example.mediaservice.dto;

public record MediaMetadataResponse(
        String id,
        String sellerId,
        String contentType,
        long sizeBytes
) {}
