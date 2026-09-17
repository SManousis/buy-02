package com.example.orderservice.client;

import com.example.orderservice.config.AppProperties;
import com.example.orderservice.dto.StockAdjustmentRequest;
import com.example.orderservice.exception.CartConflictException;
import com.example.orderservice.exception.NotFoundException;
import com.example.orderservice.exception.ProductServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ProductClient {
    private final RestClient client;
    public ProductClient(RestClient.Builder builder, AppProperties properties) {
        this.client = builder.baseUrl(properties.product().baseUrl()).build();
    }
    public ProductSnapshot getProduct(String productId) {
        return getProduct(productId, null);
    }

    public ProductSnapshot getProduct(String productId, String bearerToken) {
        try {
            var request = client.get().uri("/products/{id}", productId);
            if (bearerToken != null) request.header("Authorization", bearerToken);
            ProductSnapshot product = request.retrieve().body(ProductSnapshot.class);
            if (product == null) throw new ProductServiceUnavailableException();
            return product;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new NotFoundException("Product not found: " + productId);
        } catch (RestClientException ex) {
            throw new ProductServiceUnavailableException();
        }
    }

    public ProductSnapshot adjustStock(String productId, int delta, String bearerToken) {
        try {
            ProductSnapshot product = client.patch()
                    .uri("/products/{id}/stock", productId)
                    .header("Authorization", bearerToken)
                    .body(new StockAdjustmentRequest(delta))
                    .retrieve()
                    .body(ProductSnapshot.class);
            if (product == null) throw new ProductServiceUnavailableException();
            return product;
        } catch (HttpClientErrorException.Conflict ex) {
            throw new CartConflictException(
                    "Insufficient stock for product: " + productId);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new NotFoundException("Product not found: " + productId);
        } catch (RestClientException ex) {
            throw new ProductServiceUnavailableException();
        }
    }
}
