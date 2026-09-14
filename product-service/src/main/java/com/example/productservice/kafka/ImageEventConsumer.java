package com.example.productservice.kafka;

import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageEventConsumer {

    private final ProductRepository productRepository;

    // Remove deleted image ID from any products that still reference it
    @KafkaListener(topics = "${app.kafka.topics.image-deleted}", groupId = "product-service")
    public void onImageDeleted(ImageDeletedEvent event) {
        log.debug("Received IMAGE_DELETED event: mediaId={} sellerId={}", event.mediaId(), event.sellerId());

        List<Product> affected = productRepository.findByImageIdsContaining(event.mediaId());
        if (affected.isEmpty()) return;

        for (Product product : affected) {
            product.getImageIds().remove(event.mediaId());
            productRepository.save(product);
        }
        log.info("Removed stale imageId={} from {} product(s)", event.mediaId(), affected.size());
    }
}
