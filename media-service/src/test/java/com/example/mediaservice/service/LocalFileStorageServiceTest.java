package com.example.mediaservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.mediaservice.config.AppProperties;

class LocalFileStorageServiceTest {

    @TempDir
    Path storageRoot;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                null,
                null,
                new AppProperties.StorageProperties(storageRoot.toString(), "/api/media/images"),
                null);
        storageService = new LocalFileStorageService(properties);
    }

    @Test
    void storesValidatedBytesUnderAGeneratedRelativeFilename() throws Exception {
        byte[] bytes = {1, 2, 3, 4};

        String storageKey = storageService.storeFile(
                new ValidatedImage(bytes, "image/png", "png"));

        assertThat(storageKey).matches("[0-9a-f-]{36}\\.png");
        assertThat(Path.of(storageKey)).isRelative();
        assertThat(Files.readAllBytes(storageRoot.resolve(storageKey))).isEqualTo(bytes);
        try (InputStream input = storageService.getFile(storageKey)) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void deletesOnlyFilesResolvedInsideTheStorageRoot() throws Exception {
        String storageKey = storageService.storeFile(
                new ValidatedImage(new byte[] {5}, "image/jpeg", "jpg"));

        storageService.deleteFile(storageKey);

        assertThat(storageRoot.resolve(storageKey)).doesNotExist();
        assertThatThrownBy(() -> storageService.getFile("../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage key");
        assertThatThrownBy(() -> storageService.deleteFile(storageRoot.resolve("../outside.txt").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage key");
    }
}
