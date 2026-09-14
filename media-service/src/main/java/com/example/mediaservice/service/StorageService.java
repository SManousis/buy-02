package com.example.mediaservice.service;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {
    String storeFile(ValidatedImage image) throws IOException;
    InputStream getFile(String storageKey) throws IOException;
    void deleteFile(String storageKey) throws IOException;
}
