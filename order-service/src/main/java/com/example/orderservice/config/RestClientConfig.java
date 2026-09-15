package com.example.orderservice.config;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    RestClient.Builder restClientBuilder() {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2)).withReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}
