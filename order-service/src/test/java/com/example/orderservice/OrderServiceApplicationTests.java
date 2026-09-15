package com.example.orderservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class OrderServiceApplicationTests {
    @Test
    void applicationIsAValidSpringBootEntryPoint() {
        assertThat(OrderServiceApplication.class).hasAnnotation(SpringBootApplication.class);
    }
}
