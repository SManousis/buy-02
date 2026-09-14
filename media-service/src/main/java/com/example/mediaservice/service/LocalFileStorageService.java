package com.example.mediaservice.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.mediaservice.config.AppProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements StorageService {

    private static final Set<String> SAFE_EXTENSIONS = Set.of("jpg", "png", "webp");

    private final AppProperties appProperties;

    @Override
    public String storeFile(ValidatedImage image) throws IOException {
        if (!SAFE_EXTENSIONS.contains(image.extension())) {
            throw new IllegalArgumentException("Unsupported validated image extension");
        }

        Path baseDirectory = baseDirectory();
        Files.createDirectories(baseDirectory);
        String storageKey = UUID.randomUUID() + "." + image.extension();
        Path destination = resolveStorageKey(storageKey);
        Files.write(destination, image.content(), StandardOpenOption.CREATE_NEW);
        return storageKey;
    }

    @Override
    public InputStream getFile(String storageKey) throws IOException {
        return Files.newInputStream(resolveStorageKey(storageKey));
    }

    @Override
    public void deleteFile(String storageKey) throws IOException {
        Files.deleteIfExists(resolveStorageKey(storageKey));
    }

    private Path baseDirectory() {
        return Paths.get(appProperties.storage().basePath()).toAbsolutePath().normalize();
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Invalid storage key");
        }

        Path keyPath;
        try {
            keyPath = Path.of(storageKey);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid storage key", exception);
        }

        Path baseDirectory = baseDirectory();
        Path resolved = keyPath.isAbsolute()
                ? keyPath.normalize()
                : baseDirectory.resolve(keyPath).normalize();
        if (!resolved.startsWith(baseDirectory) || resolved.equals(baseDirectory)) {
            throw new IllegalArgumentException("Invalid storage key: path leaves the storage root");
        }
        return resolved;
    }
}
