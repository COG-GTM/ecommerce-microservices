package com.ibatulanand.apigateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRateLimitFilterTest {

    private final MutableClock clock = new MutableClock();
    private final OrderRateLimitFilter filter =
            new OrderRateLimitFilter(new OrderRateLimiter(3, Duration.ofMinutes(1), 1000, clock));
    private final AtomicInteger forwarded = new AtomicInteger();
    private final WebFilterChain chain = exchange -> {
        forwarded.incrementAndGet();
        return Mono.empty();
    };

    @Test
    void limitsOrdersPerUserWithoutAffectingOtherUsers() {
        for (int i = 0; i < 3; i++) {
            assertThat(placeOrder("mallory").getResponse().getStatusCode()).isNull();
        }
        ServerWebExchange limited = placeOrder("mallory");
        assertThat(limited.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");

        assertThat(placeOrder("alice").getResponse().getStatusCode()).isNull();
        assertThat(forwarded).hasValue(4);
    }

    @Test
    void windowResets() {
        for (int i = 0; i < 4; i++) {
            placeOrder("mallory");
        }
        clock.advance(Duration.ofMinutes(1));
        assertThat(placeOrder("mallory").getResponse().getStatusCode()).isNull();
        assertThat(forwarded).hasValue(4);
    }

    @Test
    void otherRoutesAreNotLimited() {
        for (int i = 0; i < 10; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/product"));
            filter.filter(exchange, chain).block();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
        assertThat(forwarded).hasValue(10);
    }

    @Test
    void trackedClientCapFailsClosedForNewClientsUntilWindowsExpire() {
        OrderRateLimiter limiter = new OrderRateLimiter(5, Duration.ofMinutes(1), 2, clock);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue();
        assertThat(limiter.tryAcquire("c")).isFalse();
        assertThat(limiter.tryAcquire("a")).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("c")).isTrue();
    }

    @Test
    void equivalentOrderPathsShareTheQuota() {
        for (String path : new String[]{"/api/order", "/api/order/", "/api/order;x=1"}) {
            placeOrder("mallory", path);
        }
        assertThat(placeOrder("mallory", "/api//order").getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(forwarded).hasValue(3);
    }

    @Test
    void trackedClientCapTellsNewClientsToWaitForAWindow() {
        OrderRateLimiter limiter = new OrderRateLimiter(5, Duration.ofMinutes(1), 1, clock);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isFalse();
        assertThat(limiter.retryAfterSeconds("b")).isEqualTo(60);
    }

    @Test
    void springCanCreateTheFilterFromProperties() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            context.register(OrderRateLimitFilter.class);
            context.refresh();
            assertThat(context.getBean(OrderRateLimitFilter.class)).isNotNull();
        }
    }

    private ServerWebExchange placeOrder(String user) {
        return placeOrder(user, "/api/order");
    }

    private ServerWebExchange placeOrder(String user, String path) {
        Principal principal = () -> user;
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path))
                .mutate().principal(Mono.just(principal)).build();
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
