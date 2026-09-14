package com.example.mediaservice.service;

import java.util.Objects;

/** Image bytes and media properties established from the content itself. */
public record ValidatedImage(byte[] content, String contentType, String extension) {

    public ValidatedImage {
        content = Objects.requireNonNull(content, "content").clone();
        contentType = Objects.requireNonNull(contentType, "contentType");
        extension = Objects.requireNonNull(extension, "extension");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
