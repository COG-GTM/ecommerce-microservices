package com.ibatulanand.apigateway.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Limits POST /api/order per authenticated client. Runs after the Spring Security filter chain,
 * so the exchange principal is the validated JWT subject.
 */
@Component
public class OrderRateLimitFilter implements WebFilter, Ordered {

    private static final List<String> ORDER_PATH_SEGMENTS = List.of("api", "order");

    private final OrderRateLimiter limiter;

    @Autowired
    public OrderRateLimitFilter(@Value("${gateway.rate-limit.orders.max-requests:10}") int maxRequests,
                                @Value("${gateway.rate-limit.orders.window:1m}") Duration window,
                                @Value("${gateway.rate-limit.orders.max-tracked-clients:100000}") int maxTrackedClients) {
        this(new OrderRateLimiter(maxRequests, window, maxTrackedClients, Clock.systemUTC()));
    }

    OrderRateLimitFilter(OrderRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())
                || !isOrderPath(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .map(principal -> "user:" + principal.getName())
                .defaultIfEmpty("ip:" + remoteAddress(exchange))
                .flatMap(clientKey -> {
                    if (limiter.tryAcquire(clientKey)) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER,
                            String.valueOf(limiter.retryAfterSeconds(clientKey)));
                    return exchange.getResponse().setComplete();
                });
    }

    // Compare decoded segments without matrix params or empty segments, so "/api/order/",
    // "/api//order" and "/api/order;x=1" cannot bypass the limit.
    private static boolean isOrderPath(PathContainer path) {
        return path.elements().stream()
                .filter(PathContainer.PathSegment.class::isInstance)
                .map(element -> ((PathContainer.PathSegment) element).valueToMatch())
                .filter(segment -> !segment.isEmpty())
                .toList()
                .equals(ORDER_PATH_SEGMENTS);
    }

    private static String remoteAddress(ServerWebExchange exchange) {
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        return address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
