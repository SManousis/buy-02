package com.example.mediaservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.mediaservice.config.AppProperties;
import com.example.mediaservice.kafka.ImageEventProducer;
import com.example.mediaservice.repository.MediaAssetRepository;
import com.example.mediaservice.service.ImageContentValidator;
import com.example.mediaservice.service.MediaService;
import com.example.mediaservice.service.StorageService;

class MediaServiceApplicationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AppProperties.class, () -> new AppProperties(
                    new AppProperties.JwtProperties(
                            "test-secret-that-is-at-least-32-characters-long", 86_400_000,
                            "user-service", "buy-01-api"),
                    new AppProperties.CorsProperties(List.of("http://localhost:4200")),
                    new AppProperties.StorageProperties("test-storage", "/api/media/images"),
                    new AppProperties.KafkaProperties(new AppProperties.KafkaProperties.Topics(
                            "image.uploaded", "image.deleted", "product.deleted"))))
            .withBean(MediaAssetRepository.class, () -> mock(MediaAssetRepository.class))
            .withBean(StorageService.class, () -> mock(StorageService.class))
            .withBean(ImageEventProducer.class, () -> mock(ImageEventProducer.class))
            .withUserConfiguration(ImageContentValidator.class, MediaService.class);

    @Test
    void mediaDomainContextLoadsWithoutMongoOrKafka() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MediaService.class);
            assertThat(context).hasSingleBean(ImageContentValidator.class);
        });
    }
}
