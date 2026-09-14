package com.example.discoveryservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServiceApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void standaloneRegistryDoesNotRegisterWithItself() {
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("discovery-service");
        assertThat(environment.getProperty("eureka.client.register-with-eureka", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("eureka.client.fetch-registry", Boolean.class))
                .isFalse();
    }
}
