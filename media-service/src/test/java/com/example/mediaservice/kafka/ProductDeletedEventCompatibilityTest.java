package com.example.mediaservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProductDeletedEventCompatibilityTest {

    @Test
    void readsLegacyImageUrlsFromQueuedDeletionEvents() throws Exception {
        ProductDeletedEvent event = new ObjectMapper().readValue("""
                {
                  "eventType": "PRODUCT_DELETED",
                  "productId": "product-1",
                  "sellerId": "seller-1",
                  "imageUrls": ["media-1", "media-2"]
                }
                """, ProductDeletedEvent.class);

        assertThat(event.imageIds()).containsExactly("media-1", "media-2");
    }
}
