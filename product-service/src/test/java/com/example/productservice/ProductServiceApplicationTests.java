package com.example.productservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import com.example.productservice.client.MediaOwnershipClient;
import com.example.productservice.config.AppProperties;
import com.example.productservice.kafka.ProductEventProducer;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.service.ProductService;

class ProductServiceApplicationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AppProperties.class, () -> new AppProperties(
                    new AppProperties.JwtProperties(
                            "test-secret-that-is-at-least-32-characters-long", 86_400_000,
                            "user-service", "buy-01-api"),
                    new AppProperties.CorsProperties(java.util.List.of("http://localhost:4200")),
                    new AppProperties.KafkaProperties(new AppProperties.KafkaProperties.Topics(
                            "product.created", "product.updated", "product.deleted", "image.deleted")),
                    new AppProperties.MediaProperties("media-service")))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ProductRepository.class, () -> mock(ProductRepository.class))
            .withBean(ProductEventProducer.class, () -> mock(ProductEventProducer.class))
            .withUserConfiguration(MediaOwnershipClient.class, ProductService.class);

    @Test
    void productDomainContextLoadsWithoutExternalInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductService.class);
            assertThat(context).hasSingleBean(MediaOwnershipClient.class);
        });
    }
}
