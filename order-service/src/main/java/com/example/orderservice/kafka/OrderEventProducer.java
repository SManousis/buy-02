package com.example.orderservice.kafka;

import com.example.orderservice.config.AppProperties;
import com.example.orderservice.model.Order;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventProducer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final AppProperties properties;

    public OrderEventProducer(KafkaTemplate<String, OrderEvent> kafkaTemplate, AppProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publishCreated(Order order) {
        publish(properties.kafka().topics().orderCreated(), "ORDER_CREATED", order);
    }

    public void publishStatusChanged(Order order) {
        publish(properties.kafka().topics().orderStatusChanged(), "ORDER_STATUS_CHANGED", order);
    }

    public void publishCancelled(Order order) {
        publish(properties.kafka().topics().orderCancelled(), "ORDER_CANCELLED", order);
    }

    private void publish(String topic, String eventType, Order order) {
        OrderEvent event = new OrderEvent(eventType, order.getId(), order.getCheckoutGroupId(),
                order.getBuyerId(), order.getSellerId(), order.getSubtotal(), order.getStatus(), Instant.now());
        kafkaTemplate.send(topic, order.getId(), event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Kafka publish failed: topic={} eventType={} orderId={} error={}",
                        topic, eventType, order.getId(), error.getMessage());
            } else {
                log.debug("Kafka publish ok: topic={} eventType={} orderId={}",
                        topic, eventType, order.getId());
            }
        });
    }
}
