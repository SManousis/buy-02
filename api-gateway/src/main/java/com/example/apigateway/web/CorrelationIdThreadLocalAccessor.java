package com.example.apigateway.web;

import org.slf4j.MDC;

import io.micrometer.context.ThreadLocalAccessor;

/** Bridges the request's Reactor Context to just its MDC entry, preserving other MDC keys. */
public final class CorrelationIdThreadLocalAccessor implements ThreadLocalAccessor<String> {

    public static final String KEY = CorrelationIdThreadLocalAccessor.class.getName();
    private static final String MDC_KEY = "correlationId";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public String getValue() {
        return MDC.get(MDC_KEY);
    }

    @Override
    public void setValue(String value) {
        MDC.put(MDC_KEY, value);
    }

    @Override
    public void setValue() {
        MDC.remove(MDC_KEY);
    }
}
