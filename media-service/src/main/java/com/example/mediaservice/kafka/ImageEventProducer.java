package com.example.mediaservice.kafka;

import java.time.Instant;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.mediaservice.config.AppProperties;
import com.example.mediaservice.model.MediaAsset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEventProducer {

    private final KafkaTemplate<String, ImageEvent> kafkaTemplate;
    private final AppProperties appProperties;

    public void publishImageUploaded(MediaAsset asset) {
        publish(
                appProperties.kafka().topics().imageUploaded(),
                buildEvent("IMAGE_UPLOADED", asset)
        );
    }

    public void publishImageDeleted(MediaAsset asset) {
        publish(
                appProperties.kafka().topics().imageDeleted(),
                buildEvent("IMAGE_DELETED", asset)
        );
    }

    private void publish(String topic, ImageEvent event) {
        kafkaTemplate.send(topic, event.mediaId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka publish failed: topic={} eventType={} mediaId={} error={}",
                                topic, event.eventType(), event.mediaId(), ex.getMessage());
                    } else {
                        log.debug("Kafka publish ok: topic={} eventType={} mediaId={} offset={}",
                                topic, event.eventType(), event.mediaId(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private ImageEvent buildEvent(String eventType, MediaAsset asset) {
        return new ImageEvent(
                eventType,
                asset.getId(),
                asset.getSellerId(),
                asset.getOriginalFileName(),
                asset.getContentType(),
                asset.getSizeBytes(),
                Instant.now()
        );
    }
}
