package com.example.userservice.config;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    RestClient.Builder restClientBuilder() {
        return restClientBuilder(ClientHttpRequestFactoryBuilder.detect());
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return restClientBuilder(ClientHttpRequestFactoryBuilder.detect());
    }

    RestClient.Builder restClientBuilder(
            ClientHttpRequestFactoryBuilder<? extends ClientHttpRequestFactory> requestFactoryBuilder
    ) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(requestFactoryBuilder.build(settings));
    }
}
