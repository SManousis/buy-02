package com.example.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    private final AppProperties properties;

    public KafkaConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public NewTopic orderCreatedTopic() {
        return topic(properties.kafka().topics().orderCreated());
    }

    @Bean
    public NewTopic orderStatusChangedTopic() {
        return topic(properties.kafka().topics().orderStatusChanged());
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return topic(properties.kafka().topics().orderCancelled());
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
