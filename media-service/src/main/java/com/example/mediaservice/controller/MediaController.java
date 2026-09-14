package com.example.mediaservice.controller;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.mediaservice.dto.MediaUploadResponse;
import com.example.mediaservice.dto.MediaMetadataResponse;
import com.example.mediaservice.model.MediaAsset;
import com.example.mediaservice.service.MediaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/images")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<MediaUploadResponse> uploadImage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file
    ) {
        MediaUploadResponse response = mediaService.uploadImage(jwt.getSubject(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<InputStreamResource> getImage(@PathVariable String id) {
        MediaAsset asset = mediaService.getAssetById(id);
        InputStream stream = mediaService.getImageContent(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(asset.getContentType()));
        headers.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).mustRevalidate());
        headers.setContentLength(asset.getSizeBytes());
        String safeFilename = Objects.requireNonNullElse(asset.getOriginalFileName(), "image")
                .replaceAll("[\\r\\n\\\"]", "_");
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(safeFilename, StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
            .headers(headers)
            .body(new InputStreamResource(stream));
    }

    @GetMapping("/images/{id}/metadata")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<MediaMetadataResponse> getImageMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id
    ) {
        return ResponseEntity.ok(mediaService.getMetadata(jwt.getSubject(), id));
    }

    @DeleteMapping("/images/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> deleteImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id
    ) {
        mediaService.deleteImage(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
