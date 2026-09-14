package com.example.mediaservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final AppProperties appProperties;

    @Bean
    public NewTopic imageUploadedTopic() {
        return TopicBuilder.name(appProperties.kafka().topics().imageUploaded())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic imageDeletedTopic() {
        return TopicBuilder.name(appProperties.kafka().topics().imageDeleted())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
