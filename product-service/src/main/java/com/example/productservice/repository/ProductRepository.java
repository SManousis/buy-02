package com.example.productservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.productservice.model.Product;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findBySellerId(String sellerId);

    Optional<Product> findByIdAndSellerId(String id, String sellerId);

    List<Product> findByImageIdsContaining(String imageId);
}
