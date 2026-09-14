package com.example.productservice.config;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient.Builder mediaRestClientBuilder() {
        return mediaRestClientBuilder(ClientHttpRequestFactoryBuilder.detect());
    }

    RestClient.Builder mediaRestClientBuilder(
            ClientHttpRequestFactoryBuilder<? extends ClientHttpRequestFactory> requestFactoryBuilder
    ) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(requestFactoryBuilder.build(settings));
    }
}
