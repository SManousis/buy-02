package com.example.productservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProductEventCompatibilityTest {

    @Test
    void readsLegacyImageUrlsFromQueuedProductEvents() throws Exception {
        ProductEvent event = new ObjectMapper().readValue("""
                {
                  "eventType": "PRODUCT_DELETED",
                  "productId": "product-1",
                  "sellerId": "seller-1",
                  "imageUrls": ["media-1"]
                }
                """, ProductEvent.class);

        assertThat(event.imageIds()).containsExactly("media-1");
    }
}
