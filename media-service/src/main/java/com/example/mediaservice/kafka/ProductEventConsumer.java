package com.example.mediaservice.kafka;

import com.example.mediaservice.exception.MediaNotFoundException;
import com.example.mediaservice.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final MediaService mediaService;

    // Delete all images belonging to a product when the product is removed
    @KafkaListener(topics = "${app.kafka.topics.product-deleted}", groupId = "media-service")
    public void onProductDeleted(ProductDeletedEvent event) {
        if (!"PRODUCT_DELETED".equals(event.eventType())) return;
        if (event.imageIds() == null || event.imageIds().isEmpty()) return;

        log.debug("Received PRODUCT_DELETED: productId={} images={}", event.productId(), event.imageIds().size());

        int deleted = 0;
        for (String imageId : event.imageIds()) {
            try {
                mediaService.deleteImage(event.sellerId(), imageId);
                deleted++;
            } catch (MediaNotFoundException ignored) {
                // already deleted — nothing to do
            }
        }
        log.info("Cleaned up {}/{} image(s) for deleted productId={}", deleted, event.imageIds().size(), event.productId());
    }
}
