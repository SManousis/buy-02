package com.example.userservice.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Attaches a correlation ID to every request so log lines across a single
 * HTTP call can be grouped — critical when aggregating logs from multiple services.
 *
 * Strategy:
 *  1. Use the X-Correlation-ID header from the caller (e.g., gateway) if present.
 *  2. Otherwise generate a new UUID.
 *  3. Echo it back in the response header so clients and the gateway can trace responses.
 *  4. Always remove it from MDC after the request to avoid leaking into pooled threads.
 */
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY               = "correlationId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String requestedId = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId = requestedId != null
                && SAFE_CORRELATION_ID.matcher(requestedId).matches()
                ? requestedId : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);  // must run even if the handler throws
        }
    }
}
