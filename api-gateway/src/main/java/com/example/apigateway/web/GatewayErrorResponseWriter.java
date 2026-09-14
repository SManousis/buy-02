package com.example.apigateway.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class GatewayErrorResponseWriter {

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status,
                            String error, String message) {
        String correlationId = exchange.getAttributeOrDefault(
                CorrelationIdWebFilter.ATTRIBUTE_NAME, "unavailable");
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\","
                        + "\"message\":\"%s\",\"correlationId\":\"%s\"}",
                Instant.now(), status.value(), error, message, correlationId);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

