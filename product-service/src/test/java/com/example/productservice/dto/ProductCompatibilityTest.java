package com.example.productservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsLegacyImageUrlsInCreateRequestsButWritesOnlyCanonicalImageIds() throws Exception {
        CreateProductRequest request = objectMapper.readValue("""
                {
                  "name": "Name",
                  "description": "Description",
                  "price": 10.00,
                  "stock": 1,
                  "imageUrls": ["media-1", "media-2"]
                }
                """, CreateProductRequest.class);

        assertThat(request.imageIds()).containsExactly("media-1", "media-2");
        assertThat(request.price()).isEqualByComparingTo(new BigDecimal("10.00"));

        String serialized = objectMapper.writeValueAsString(request);
        assertThat(serialized).contains("\"imageIds\"").doesNotContain("imageUrls");
    }

    @Test
    void readsLegacyImageUrlsInUpdateRequests() throws Exception {
        UpdateProductRequest request = objectMapper.readValue(
                "{\"imageUrls\":[\"media-3\"]}", UpdateProductRequest.class);

        assertThat(request.imageIds()).isEqualTo(List.of("media-3"));
    }
}
