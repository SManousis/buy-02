package com.example.mediaservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.example.mediaservice.config.AppProperties;
import com.example.mediaservice.dto.MediaMetadataResponse;
import com.example.mediaservice.dto.MediaUploadResponse;
import com.example.mediaservice.exception.MediaNotFoundException;
import com.example.mediaservice.kafka.ImageEventProducer;
import com.example.mediaservice.model.MediaAsset;
import com.example.mediaservice.repository.MediaAssetRepository;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaAssetRepository repository;
    @Mock
    private StorageService storageService;
    @Mock
    private ImageContentValidator imageValidator;
    @Mock
    private ImageEventProducer eventProducer;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                null,
                null,
                new AppProperties.StorageProperties("/tmp/media-test", "/api/media/images/"),
                null);
        mediaService = new MediaService(
                repository, storageService, properties, eventProducer, imageValidator);
    }

    @Test
    void persistsOnlyValidatedPropertiesAndReturnsTheGatewayUrl() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "../../evil\r\n\".png", "image/jpeg", new byte[] {9});
        ValidatedImage validated = new ValidatedImage(
                new byte[] {1, 2, 3}, "image/png", "png");
        when(imageValidator.validate(upload)).thenReturn(validated);
        when(storageService.storeFile(validated)).thenReturn("generated-id.png");
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            asset.setId("media-1");
            asset.setCreatedAt(Instant.parse("2026-09-02T12:00:00Z"));
            return asset;
        });

        MediaUploadResponse response = mediaService.uploadImage("seller-1", upload);

        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        verify(repository).save(captor.capture());
        MediaAsset persisted = captor.getValue();
        assertThat(persisted.getOriginalFileName()).isEqualTo("evil___.png");
        assertThat(persisted.getStoredFileName()).isEqualTo("generated-id.png");
        assertThat(persisted.getContentType()).isEqualTo("image/png");
        assertThat(persisted.getSizeBytes()).isEqualTo(3);
        assertThat(persisted.getStorageKey()).isEqualTo("generated-id.png");
        assertThat(response.getUrl()).isEqualTo("/api/media/images/media-1");
        verify(eventProducer).publishImageUploaded(persisted);
    }

    @Test
    void removesTheStoredFileWhenMetadataPersistenceFails() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[] {1});
        ValidatedImage validated = new ValidatedImage(new byte[] {1}, "image/png", "png");
        when(imageValidator.validate(upload)).thenReturn(validated);
        when(storageService.storeFile(validated)).thenReturn("generated.png");
        when(repository.save(any(MediaAsset.class))).thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> mediaService.uploadImage("seller-1", upload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database down");

        verify(storageService).deleteFile("generated.png");
    }

    @Test
    void retainsThePersistenceFailureWhenOrphanCleanupAlsoFails() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[] {1});
        ValidatedImage validated = new ValidatedImage(new byte[] {1}, "image/png", "png");
        IllegalStateException persistenceFailure = new IllegalStateException("database down");
        IOException cleanupFailure = new IOException("storage unavailable");
        when(imageValidator.validate(upload)).thenReturn(validated);
        when(storageService.storeFile(validated)).thenReturn("generated.png");
        when(repository.save(any(MediaAsset.class))).thenThrow(persistenceFailure);
        doThrow(cleanupFailure).when(storageService).deleteFile("generated.png");

        assertThatThrownBy(() -> mediaService.uploadImage("seller-1", upload))
                .isSameAs(persistenceFailure)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .containsExactly(cleanupFailure));
    }

    @Test
    void reportsMissingStorageContentAsMediaNotFound() throws Exception {
        MediaAsset asset = MediaAsset.builder().id("media-1").storageKey("missing.png").build();
        when(repository.findById("media-1")).thenReturn(Optional.of(asset));
        doThrow(new IOException("gone")).when(storageService).getFile("missing.png");

        assertThatThrownBy(() -> mediaService.getImageContent("media-1"))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining("content");
    }

    @Test
    void reportsCorruptStorageContentAsMediaNotFound() throws Exception {
        MediaAsset asset = MediaAsset.builder()
                .id("media-1")
                .storageKey("damaged.png")
                .contentType("image/png")
                .build();
        when(repository.findById("media-1")).thenReturn(Optional.of(asset));
        when(storageService.getFile("damaged.png"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(imageValidator.validateStored(any(byte[].class), any(String.class)))
                .thenThrow(new IllegalArgumentException("corrupt"));

        assertThatThrownBy(() -> mediaService.getImageContent("media-1"))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining("content");
    }

    @Test
    void returnsOnlyPublicMetadataForTheOwningSeller() {
        MediaAsset asset = MediaAsset.builder()
                .id("media-1")
                .sellerId("seller-1")
                .contentType("image/png")
                .sizeBytes(42L)
                .storageKey("private/generated.png")
                .createdAt(Instant.parse("2026-09-02T12:00:00Z"))
                .build();
        when(repository.findById("media-1")).thenReturn(Optional.of(asset));

        assertThat(mediaService.getMetadata("seller-1", "media-1"))
                .isEqualTo(new MediaMetadataResponse("media-1", "seller-1", "image/png", 42L));
        assertThatThrownBy(() -> mediaService.getMetadata("seller-2", "media-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preventsASellerFromDeletingAnotherSellersImage() throws Exception {
        MediaAsset asset = MediaAsset.builder()
                .id("media-1")
                .sellerId("seller-1")
                .storageKey("private/generated.png")
                .build();
        when(repository.findById("media-1")).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> mediaService.deleteImage("seller-2", "media-1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not own this media asset");

        verify(storageService, never()).deleteFile(any());
        verify(repository, never()).delete(any(MediaAsset.class));
        verify(eventProducer, never()).publishImageDeleted(any(MediaAsset.class));
    }
}
