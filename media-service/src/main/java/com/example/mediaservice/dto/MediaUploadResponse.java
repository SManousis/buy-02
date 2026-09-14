package com.example.mediaservice.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaUploadResponse {
    private String id;
    private String sellerId;
    private String originalFileName;
    private String storedFileName;
    private String contentType;
    private long size;
    private String url;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
