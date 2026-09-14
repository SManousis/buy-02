package com.example.productservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

class RestClientConfigTest {

    @Test
    void configuresBoundedConnectAndReadTimeouts() {
        CapturingRequestFactoryBuilder factoryBuilder = new CapturingRequestFactoryBuilder();

        new RestClientConfig().mediaRestClientBuilder(factoryBuilder);

        assertThat(factoryBuilder.settings.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(factoryBuilder.settings.readTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    private static final class CapturingRequestFactoryBuilder
            implements ClientHttpRequestFactoryBuilder<ClientHttpRequestFactory> {

        private HttpClientSettings settings;

        @Override
        public ClientHttpRequestFactory build(HttpClientSettings settings) {
            this.settings = settings;
            return new SimpleClientHttpRequestFactory();
        }
    }
}
