package com.example.mediaservice.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.mediaservice.config.AppProperties;
import com.example.mediaservice.dto.MediaMetadataResponse;
import com.example.mediaservice.dto.MediaUploadResponse;
import com.example.mediaservice.exception.MediaNotFoundException;
import com.example.mediaservice.kafka.ImageEventProducer;
import com.example.mediaservice.model.MediaAsset;
import com.example.mediaservice.repository.MediaAssetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final StorageService storageService;
    private final AppProperties appProperties;
    private final ImageEventProducer eventProducer;
    private final ImageContentValidator imageValidator;

    @Transactional
    public MediaUploadResponse uploadImage(String sellerId, MultipartFile file) {
        ValidatedImage validatedImage = imageValidator.validate(file);
        String storageKey;

        try {
            storageKey = storageService.storeFile(validatedImage);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store uploaded image", ex);
        }

        MediaAsset asset = MediaAsset.builder()
            .sellerId(sellerId)
            .originalFileName(sanitizeOriginalFileName(file.getOriginalFilename(), validatedImage.extension()))
            .storedFileName(Path.of(storageKey).getFileName().toString())
            .contentType(validatedImage.contentType())
            .sizeBytes(validatedImage.content().length)
            .storageKey(storageKey)
            .build();

        final MediaAsset saved;
        try {
            saved = mediaAssetRepository.save(asset);
        } catch (RuntimeException exception) {
            deleteStoredFileAfterFailedSave(storageKey, exception);
            throw exception;
        }

        String publicUrl = stripTrailingSlashes(appProperties.storage().publicBaseUrl()) + "/" + saved.getId();
        eventProducer.publishImageUploaded(saved);

        return MediaUploadResponse.builder()
            .id(saved.getId())
            .sellerId(saved.getSellerId())
            .originalFileName(saved.getOriginalFileName())
            .storedFileName(saved.getStoredFileName())
            .contentType(saved.getContentType())
            .size(saved.getSizeBytes())
            .url(publicUrl)
            .createdAt(saved.getCreatedAt())
            .build();
    }

    public MediaAsset getAssetById(String id) {
        return mediaAssetRepository.findById(id)
            .orElseThrow(() -> new MediaNotFoundException("Media not found with id: " + id));
    }

    public MediaMetadataResponse getMetadata(String sellerId, String assetId) {
        MediaAsset asset = getAssetById(assetId);
        if (!asset.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("You do not own this media asset");
        }
        return new MediaMetadataResponse(
                asset.getId(), asset.getSellerId(), asset.getContentType(), asset.getSizeBytes());
    }

    public InputStream getImageContent(String assetId) {
        MediaAsset asset = getAssetById(assetId);
        try (InputStream input = storageService.getFile(asset.getStorageKey())) {
            ValidatedImage validatedImage = imageValidator.validateStored(
                    input.readAllBytes(), asset.getContentType());
            return new ByteArrayInputStream(validatedImage.content());
        } catch (IOException | IllegalArgumentException ex) {
            throw new MediaNotFoundException("Media content is unavailable for id: " + assetId, ex);
        }
    }

    @Transactional
    public void deleteImage(String sellerId, String assetId) {
        MediaAsset asset = getAssetById(assetId);
        if (!asset.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("You do not own this media asset");
        }

        try {
            storageService.deleteFile(asset.getStorageKey());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete media file", ex);
        }

        mediaAssetRepository.delete(asset);
        eventProducer.publishImageDeleted(asset);
    }

    private void deleteStoredFileAfterFailedSave(String storageKey, RuntimeException originalException) {
        try {
            storageService.deleteFile(storageKey);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
            log.error("Could not remove orphaned media file after metadata save failed: storageKey={}",
                    storageKey, cleanupException);
        }
    }

    private String sanitizeOriginalFileName(String originalFileName, String extension) {
        String candidate = Objects.requireNonNullElse(originalFileName, "")
                .replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}\\\"]", "_")
                .trim();
        if (candidate.isBlank()) {
            candidate = "image." + extension;
        }
        return candidate.length() > 120 ? candidate.substring(0, 120) : candidate;
    }

    private String stripTrailingSlashes(String value) {
        return value.replaceAll("/+$", "");
    }
}
