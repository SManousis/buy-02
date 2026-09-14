package com.example.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.example.apigateway.web.CorrelationIdWebFilter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.jwt.secret=test-secret-that-is-at-least-32-characters-long",
                "app.jwt.issuer=user-service",
                "app.jwt.audience=buy-01-api",
                "app.cors.allowed-origins=http://localhost:4200",
                "eureka.client.enabled=false"
        })
class CorrelationIdWebFilterTests {

    @Autowired
    private CorrelationIdWebFilter filter;

    private ScheduledExecutorService firstExecutor;
    private ScheduledExecutorService secondExecutor;
    private Scheduler first;
    private Scheduler second;

    @BeforeEach
    void setUp() throws Exception {
        MDC.put("correlationId", "caller-context");
        MDC.put("unrelated", "caller-value");
        firstExecutor = Executors.newSingleThreadScheduledExecutor();
        secondExecutor = Executors.newSingleThreadScheduledExecutor();
        firstExecutor.submit(() -> MDC.put("unrelated", "worker-value")).get(5, TimeUnit.SECONDS);
        secondExecutor.submit(() -> MDC.put("unrelated", "worker-value")).get(5, TimeUnit.SECONDS);
        first = Schedulers.fromExecutorService(firstExecutor);
        second = Schedulers.fromExecutorService(secondExecutor);
    }

    @AfterEach
    void tearDown() {
        first.dispose();
        second.dispose();
        MDC.clear();
    }

    @Test
    void concurrentRequestsKeepTheirMdcAcrossThreadSwitchesAndInLogEvents() throws Exception {
        Queue<ILoggingEvent> events = new ConcurrentLinkedQueue<>();
        Logger logger = (Logger) LoggerFactory.getLogger("gateway.correlation.test");
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                events.add(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        try {
            Flux.range(0, 32).flatMap(index -> {
                String id = "concurrent-" + index;
                return filter.filter(exchange(id), traced -> {
                    assertThat(MDC.get("correlationId")).isEqualTo(id);
                    assertThat(traced.getRequest().getHeaders().getFirst("X-Correlation-ID")).isEqualTo(id);
                    assertThat((String) traced.getAttribute(CorrelationIdWebFilter.ATTRIBUTE_NAME)).isEqualTo(id);
                    maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                    return Mono.delay(Duration.ofMillis(10), first)
                            .doOnNext(ignored -> assertThat(MDC.get("correlationId")).isEqualTo(id))
                            .publishOn(second)
                            .doOnNext(ignored -> {
                                assertThat(MDC.get("correlationId")).isEqualTo(id);
                                logger.info(id);
                                active.decrementAndGet();
                            })
                            .then(traced.getResponse().setComplete());
                });
            }, 8).blockLast(Duration.ofSeconds(10));

            assertThat(maxActive.get()).isGreaterThan(1);
            assertThat(events).hasSize(32).allSatisfy(event ->
                    assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", event.getFormattedMessage()));
            assertRestored();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void missingAndUnsafeIdsAreMintedAndSequentialRequestsDoNotReuseMdc() throws Exception {
        String previous = null;
        for (String requested : new String[] {"safe-client-id", null, "unsafe id"}) {
            MockServerWebExchange exchange = exchange(requested);
            filter.filter(exchange, traced -> Mono.delay(Duration.ofMillis(1), first)
                    .doOnNext(ignored -> assertThat(MDC.get("correlationId"))
                            .isEqualTo(traced.getRequest().getHeaders().getFirst("X-Correlation-ID")))
                    .then(Mono.defer(() -> {
                        traced.getResponse().getHeaders().set("X-Correlation-ID", "downstream-value");
                        return traced.getResponse().setComplete();
                    })))
                    .block(Duration.ofSeconds(5));
            String actual = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
            assertThat(exchange.getResponse().getHeaders().get("X-Correlation-ID")).containsExactly(actual);
            assertThat(actual).isNotEqualTo(previous).isNotEqualTo("caller-context");
            if ("safe-client-id".equals(requested)) {
                assertThat(actual).isEqualTo(requested);
            } else {
                assertThat(actual).matches("[0-9a-f-]{36}");
            }
            previous = actual;
            assertRestored();
        }
    }

    @Test
    void errorSignalsRestoreCallerAndWorkerMdc() throws Exception {
        assertThatThrownBy(() -> filter.filter(exchange("error-id"), traced ->
                Mono.delay(Duration.ofMillis(1), first)
                        .then(Mono.<Void>error(new IllegalStateException("expected failure")))
                        .doOnError(error -> assertThat(MDC.get("correlationId")).isEqualTo("error-id")))
                .block(Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class).hasMessage("expected failure");
        assertRestored();
    }

    @Test
    void cancellationRestoresCallerAndWorkerMdc() throws Exception {
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        Disposable subscription = filter.filter(exchange("cancel-id"), traced -> Mono.<Void>never()
                .doOnSubscribe(ignored -> {
                    assertThat(MDC.get("correlationId")).isEqualTo("cancel-id");
                    subscribed.countDown();
                })
                .doOnCancel(() -> {
                    assertThat(MDC.get("correlationId")).isEqualTo("cancel-id");
                    cancelled.countDown();
                }).subscribeOn(first)).subscribe();
        try {
            assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            subscription.dispose();
        }
        assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
        assertRestored();
    }

    private void assertRestored() throws Exception {
        assertThat(MDC.get("correlationId")).isEqualTo("caller-context");
        assertThat(MDC.get("unrelated")).isEqualTo("caller-value");
        // Read the raw workers without Reactor's wrappers, so a wrapper cannot hide a leak.
        for (ScheduledExecutorService executor : new ScheduledExecutorService[] {firstExecutor, secondExecutor}) {
            executor.submit(() -> {
                assertThat(MDC.get("correlationId")).isNull();
                assertThat(MDC.get("unrelated")).isEqualTo("worker-value");
            }).get(5, TimeUnit.SECONDS);
        }
    }

    private MockServerWebExchange exchange(String id) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/products");
        if (id != null) {
            request.header("X-Correlation-ID", id);
        }
        return MockServerWebExchange.from(request.build());
    }
}
