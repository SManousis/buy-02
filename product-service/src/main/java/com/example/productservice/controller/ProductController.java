package com.example.productservice.controller;

import com.example.productservice.dto.CreateProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.dto.UpdateProductRequest;
import com.example.productservice.dto.StockAdjustmentRequest;
import com.example.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> listProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return productService.searchProducts(q, minPrice, maxPrice, sellerId, inStock, sort, page, size);
    }

    // Must be declared before /{id} so Spring prefers this exact match
    @GetMapping("/my")
    public List<ProductResponse> getMyProducts(@AuthenticationPrincipal Jwt jwt) {
        ensureSeller(jwt);
        return productService.listProductsBySeller(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable String id) {
        return productService.getProductById(id);
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse adjustStock(
            @PathVariable String id,
            @Valid @RequestBody StockAdjustmentRequest request
    ) {
        return productService.adjustStock(id, request.delta());
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @Valid @RequestBody CreateProductRequest request
    ) {
        ensureSeller(jwt);
        ProductResponse response = productService.createProduct(jwt.getSubject(), bearerToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ensureSeller(jwt);
        return productService.updateProduct(jwt.getSubject(), bearerToken, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id
    ) {
        ensureSeller(jwt);
        productService.deleteProduct(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }

    private void ensureSeller(Jwt jwt) {
        Object role = jwt.getClaims().get("role");
        if (role == null || !"SELLER".equals(role.toString())) {
            throw new AccessDeniedException("Seller role required");
        }
    }
}
