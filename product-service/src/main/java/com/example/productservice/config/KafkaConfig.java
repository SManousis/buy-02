package com.example.productservice.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final AppProperties appProperties;

    @Bean
    public NewTopic productCreatedTopic() {
        return TopicBuilder.name(appProperties.kafka().topics().productCreated())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic productUpdatedTopic() {
        return TopicBuilder.name(appProperties.kafka().topics().productUpdated())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic productDeletedTopic() {
        return TopicBuilder.name(appProperties.kafka().topics().productDeleted())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
