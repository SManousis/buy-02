package com.example.orderservice.client;

import com.example.orderservice.config.AppProperties;
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
        try {
            ProductSnapshot product = client.get().uri("/products/{id}", productId).retrieve().body(ProductSnapshot.class);
            if (product == null) throw new ProductServiceUnavailableException();
            return product;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new NotFoundException("Product not found: " + productId);
        } catch (RestClientException ex) {
            throw new ProductServiceUnavailableException();
        }
    }
}
