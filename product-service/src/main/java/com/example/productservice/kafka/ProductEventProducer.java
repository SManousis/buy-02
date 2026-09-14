package com.example.productservice.kafka;

import com.example.productservice.config.AppProperties;
import com.example.productservice.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEventProducer {

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final AppProperties appProperties;

    public void publishProductCreated(Product product) {
        publish(appProperties.kafka().topics().productCreated(), "PRODUCT_CREATED", product);
    }

    public void publishProductUpdated(Product product) {
        publish(appProperties.kafka().topics().productUpdated(), "PRODUCT_UPDATED", product);
    }

    public void publishProductDeleted(Product product) {
        publish(appProperties.kafka().topics().productDeleted(), "PRODUCT_DELETED", product);
    }

    private void publish(String topic, String eventType, Product product) {
        ProductEvent event = new ProductEvent(
                eventType,
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getImageIds() != null ? List.copyOf(product.getImageIds()) : List.of(),
                Instant.now()
        );
        kafkaTemplate.send(topic, event.productId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka publish failed: topic={} eventType={} productId={} error={}",
                                topic, eventType, event.productId(), ex.getMessage());
                    } else {
                        log.debug("Kafka publish ok: topic={} eventType={} productId={} offset={}",
                                topic, eventType, event.productId(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
