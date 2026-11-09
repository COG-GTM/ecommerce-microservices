package com.ibatulanand.observability;

import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux counterpart of {@link CorrelationIdFilter}. The ID is forwarded to downstream
 * services as a request header, echoed on the response and stored in the Reactor context
 * under {@link CorrelationIds#MDC_KEY}; the MDC is populated from that context via
 * Reactor automatic context propagation (see {@link ObservabilityAutoConfiguration}).
 */
public class ReactiveCorrelationIdFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = CorrelationIds.resolve(
                exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER));
        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.set(CorrelationIds.HEADER, correlationId)))
                .build();
        mutated.getResponse().getHeaders().set(CorrelationIds.HEADER, correlationId);
        mutated.getAttributes().put(CorrelationIds.MDC_KEY, correlationId);
        return chain.filter(mutated)
                .contextWrite(context -> context.put(CorrelationIds.MDC_KEY, correlationId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
