package com.example.apigateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.example.apigateway.web.GatewayErrorResponseWriter;

import reactor.core.publisher.Mono;

@Component
public class GatewaySecurityErrorHandler
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final GatewayErrorResponseWriter errorWriter;

    public GatewaySecurityErrorHandler(GatewayErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                "Unauthorized", "Missing or invalid JWT token");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange,
                             org.springframework.security.access.AccessDeniedException exception) {
        return errorWriter.write(exchange, HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to access this resource");
    }
}
