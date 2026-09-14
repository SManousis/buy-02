package com.example.apigateway.web;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String ATTRIBUTE_NAME = CorrelationIdWebFilter.class.getName() + ".correlationId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = validOrNew(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        ServerWebExchange tracedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER_NAME, correlationId)))
                .build();

        tracedExchange.getAttributes().put(ATTRIBUTE_NAME, correlationId);
        tracedExchange.getResponse().beforeCommit(() -> {
            // Replace a downstream value instead of appending a duplicate header.
            tracedExchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
            return Mono.empty();
        });
        // Defer the chain so even synchronous filter code runs with the restored MDC.
        return Mono.defer(() -> chain.filter(tracedExchange))
                .contextWrite(context -> context.put(CorrelationIdThreadLocalAccessor.KEY, correlationId));
    }

    private String validOrNew(String requestedId) {
        if (requestedId != null && SAFE_CORRELATION_ID.matcher(requestedId).matches()) {
            return requestedId;
        }
        return UUID.randomUUID().toString();
    }
}
