package com.ibatulanand.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactory.RequestSizeConfig;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRouteRequestSizeTest {

    private static final long LIMIT = 100 * 1024;

    @Test
    void orderRouteRejectsBodiesAboveLimit() throws Exception {
        GatewayFilter filter = orderRouteRequestSizeFilter();

        MockServerWebExchange exchange = exchangeWithContentLength(LIMIT + 1);
        AtomicBoolean forwarded = new AtomicBoolean();
        filter.filter(exchange, e -> Mono.fromRunnable(() -> forwarded.set(true))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(forwarded).isFalse();
    }

    @Test
    void orderRouteForwardsBodiesAtLimit() throws Exception {
        GatewayFilter filter = orderRouteRequestSizeFilter();

        MockServerWebExchange exchange = exchangeWithContentLength(LIMIT);
        AtomicBoolean forwarded = new AtomicBoolean();
        filter.filter(exchange, e -> Mono.fromRunnable(() -> forwarded.set(true))).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(forwarded).isTrue();
    }

    private static GatewayFilter orderRouteRequestSizeFilter() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = OrderRouteRequestSizeTest.class.getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        GatewayProperties gateway = new Binder(new MapConfigurationPropertySource(properties))
                .bind("spring.cloud.gateway", GatewayProperties.class)
                .get();
        RouteDefinition orderRoute = gateway.getRoutes().stream()
                .filter(route -> route.getId().equals("order-service"))
                .findFirst()
                .orElseThrow();
        FilterDefinition requestSize = orderRoute.getFilters().stream()
                .filter(definition -> definition.getName().equals("RequestSize"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("order-service route has no RequestSize filter"));

        RequestSizeConfig config = new Binder(new MapConfigurationPropertySource(requestSize.getArgs()))
                .bindOrCreate(ConfigurationPropertyName.EMPTY, Bindable.of(RequestSizeConfig.class), null);
        assertThat(config.getMaxSize().toBytes()).isEqualTo(LIMIT);
        return new RequestSizeGatewayFilterFactory().apply(config);
    }

    private static MockServerWebExchange exchangeWithContentLength(long contentLength) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/order").contentLength(contentLength).build());
    }
}
