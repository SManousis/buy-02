package com.example.apigateway.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.example.apigateway.config.GatewayProperties;

import reactor.core.publisher.Mono;

@Component
public class MediaUploadSizeFilter implements GlobalFilter, Ordered {

    private final GatewayProperties properties;
    private final GatewayErrorResponseWriter errorWriter;

    public MediaUploadSizeFilter(GatewayProperties properties,
                                 GatewayErrorResponseWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        boolean isMediaUpload = exchange.getRequest().getMethod() == HttpMethod.POST
                && "/media/images".equals(exchange.getRequest().getPath().value());

        if (isMediaUpload && contentLength > properties.upload().maxRequestSize().toBytes()) {
            return errorWriter.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE,
                    "Payload Too Large", "Upload request exceeds the configured size limit");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

