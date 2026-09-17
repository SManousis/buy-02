package com.example.productservice.service;

import com.example.productservice.dto.CreateProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.dto.UpdateProductRequest;
import com.example.productservice.client.MediaOwnershipClient;
import com.example.productservice.exception.InvalidMediaReferenceException;
import com.example.productservice.exception.NotFoundException;
import com.example.productservice.exception.StockConflictException;
import com.example.productservice.kafka.ProductEventProducer;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductEventProducer eventProducer;
    private final MediaOwnershipClient mediaOwnershipClient;
    private MongoTemplate mongoTemplate;

    public ProductService(ProductRepository productRepository,
                          ProductEventProducer eventProducer,
                          MediaOwnershipClient mediaOwnershipClient) {
        this.productRepository = productRepository;
        this.eventProducer = eventProducer;
        this.mediaOwnershipClient = mediaOwnershipClient;
    }

    @Autowired(required = false)
    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Transactional
    public ProductResponse createProduct(String sellerId, String bearerToken, CreateProductRequest request) {
        validateImageIds(sellerId, bearerToken, request.imageIds(), null);
        Product product = Product.builder()
                .sellerId(sellerId)
                .name(request.name().trim())
                .description(request.description().trim())
                .price(request.price())
                .stock(request.stock())
                .imageIds(request.imageIds() == null ? new ArrayList<>() : new ArrayList<>(request.imageIds()))
                .build();

        Product saved = saveWithExclusiveImages(product);
        eventProducer.publishProductCreated(saved);
        return ProductResponse.from(saved);
    }

    public List<ProductResponse> listProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    public List<ProductResponse> listProductsBySeller(String sellerId) {
        return productRepository.findBySellerId(sellerId).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse getProductById(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse adjustStock(String productId, int delta) {
        if (delta == 0) {
            throw new IllegalArgumentException("Stock delta must not be zero");
        }

        Query query = Query.query(Criteria.where("_id").is(productId));
        if (delta < 0) {
            query.addCriteria(Criteria.where("stock").gte(-delta));
        }
        Product updated = mongoTemplate.findAndModify(
                query,
                new Update().inc("stock", delta),
                FindAndModifyOptions.options().returnNew(true),
                Product.class);
        if (updated == null) {
            if (!productRepository.existsById(productId)) {
                throw new NotFoundException("Product not found");
            }
            throw new StockConflictException("Insufficient stock for product");
        }
        eventProducer.publishProductUpdated(updated);
        return ProductResponse.from(updated);
    }

    @Transactional
    public ProductResponse updateProduct(String sellerId, String bearerToken, String productId, UpdateProductRequest request) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name().trim());
        }
        if (request.description() != null && !request.description().isBlank()) {
            product.setDescription(request.description().trim());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.stock() != null) {
            product.setStock(request.stock());
        }
        if (request.imageIds() != null) {
            validateImageIds(sellerId, bearerToken, request.imageIds(), productId);
            product.setImageIds(new ArrayList<>(request.imageIds()));
        }

        Product updated = saveWithExclusiveImages(product);
        eventProducer.publishProductUpdated(updated);
        return ProductResponse.from(updated);
    }

    @Transactional
    public void deleteProduct(String sellerId, String productId) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        productRepository.delete(product);
        eventProducer.publishProductDeleted(product);
    }

    private void validateImageIds(
            String sellerId,
            String bearerToken,
            List<String> imageIds,
            String currentProductId
    ) {
        if (imageIds == null) {
            return;
        }
        if (imageIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new InvalidMediaReferenceException("Image IDs must not be blank");
        }
        if (imageIds.size() != imageIds.stream().distinct().count()) {
            throw new InvalidMediaReferenceException("Image IDs must not contain duplicates");
        }
        imageIds.forEach(imageId -> mediaOwnershipClient.verifyOwnedImage(imageId, sellerId, bearerToken));
        for (String imageId : imageIds) {
            boolean associatedElsewhere = productRepository.findByImageIdsContaining(imageId).stream()
                    .anyMatch(existing -> !Objects.equals(existing.getId(), currentProductId));
            if (associatedElsewhere) {
                throw new InvalidMediaReferenceException(
                        "Image " + imageId + " is already associated with another product");
            }
        }
    }

    private Product saveWithExclusiveImages(Product product) {
        try {
            return productRepository.save(product);
        } catch (DuplicateKeyException exception) {
            throw new InvalidMediaReferenceException(
                    "An image is already associated with another product", exception);
        }
    }
}
