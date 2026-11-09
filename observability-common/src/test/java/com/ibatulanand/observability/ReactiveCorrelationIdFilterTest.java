package com.ibatulanand.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveCorrelationIdFilterTest {

    private final ReactiveCorrelationIdFilter filter = new ReactiveCorrelationIdFilter();

    @BeforeAll
    static void enableMdcPropagation() {
        ObservabilityAutoConfiguration.ReactorMdcPropagationConfiguration.registerMdcAccessor();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesIdAndPropagatesItDownstreamAndOnResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/order"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            forwarded.set(ex);
            return Mono.empty();
        })).verifyComplete();

        String echoed = exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER);
        assertThat(UUID.fromString(echoed)).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationIds.HEADER)).isEqualTo(echoed);
        assertThat((String) forwarded.get().getAttribute(CorrelationIds.MDC_KEY)).isEqualTo(echoed);
    }

    @Test
    void reusesIncomingIdAndExposesItInReactorContextAndMdc() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/order").header(CorrelationIds.HEADER, "abc-123"));
        AtomicReference<String> fromContext = new AtomicReference<>();
        AtomicReference<String> fromMdc = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> Mono.deferContextual(ctx -> {
                    fromContext.set(ctx.get(CorrelationIds.MDC_KEY));
                    return Mono.just(1);
                })
                .handle((value, sink) -> {
                    fromMdc.set(MDC.get(CorrelationIds.MDC_KEY));
                    sink.complete();
                })
                .then())).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER)).isEqualTo("abc-123");
        assertThat(fromContext.get()).isEqualTo("abc-123");
        assertThat(fromMdc.get()).isEqualTo("abc-123");
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeIncomingId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/order").header(CorrelationIds.HEADER, "x".repeat(200)));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            forwarded.set(ex);
            return Mono.empty();
        })).verifyComplete();

        String echoed = exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER);
        assertThat(UUID.fromString(echoed)).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationIds.HEADER)).isEqualTo(echoed);
    }
}
