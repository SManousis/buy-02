package com.example.mediaservice.repository;

import com.example.mediaservice.model.MediaAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MediaAssetRepository extends MongoRepository<MediaAsset, String> {
}
