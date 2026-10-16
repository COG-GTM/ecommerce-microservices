package com.ibatulanand.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false",
        "app.cors.allowed-origins=http://localhost:5173, http://127.0.0.1:5173"
})
class GatewayRoutingCorsTest {

    private static final String TOKEN = "test-token";
    private static final String POS_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "http://localhost:5174";

    private static final AtomicReference<String> lastStubRequest = new AtomicReference<>();
    private static final HttpServer inventoryStub = startInventoryStub();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void stubInstances(DynamicPropertyRegistry registry) {
        String stubUri = "http://localhost:" + inventoryStub.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.inventory-service[0].uri", () -> stubUri);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", () -> stubUri);
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", () -> stubUri);
    }

    @AfterAll
    static void stopStub() {
        inventoryStub.stop(0);
    }

    @BeforeEach
    void stubJwt() {
        lastStubRequest.set(null);
        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "none")
                .subject("service-account-spring-cloud-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        given(jwtDecoder.decode(anyString())).willReturn(Mono.error(new BadJwtException("bad token")));
        given(jwtDecoder.decode(TOKEN)).willReturn(Mono.just(jwt));
    }

    @Test
    void inventoryRouteReachesInventoryService() {
        webTestClient.get()
                .uri("/api/inventory?skuCode=268341-016-L&storeId=1969")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Stub", "inventory")
                .expectBody().jsonPath("$.skuCode").isEqualTo("268341-016-L");

        assertThat(lastStubRequest.get()).isEqualTo("GET /api/inventory?skuCode=268341-016-L&storeId=1969");
    }

    @Test
    void inventoryRouteRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/inventory?skuCode=268341-016-L")
                .exchange()
                .expectStatus().isUnauthorized();
        assertThat(lastStubRequest.get()).isNull();
    }

    @Test
    void routesResolveBareAndSubPaths() {
        assertThat(resolveRouteId("/api/inventory")).isEqualTo("inventory-service");
        assertThat(resolveRouteId("/api/inventory/stores/1969")).isEqualTo("inventory-service");
        assertThat(resolveRouteId("/api/product")).isEqualTo("product-service");
        assertThat(resolveRouteId("/api/product/sku/268341-016-L")).isEqualTo("product-service");
        assertThat(resolveRouteId("/api/order")).isEqualTo("order-service");
        assertThat(resolveRouteId("/api/order/ORD-1")).isEqualTo("order-service");
        assertThat(resolveRouteId("/api/unknown")).isNull();
    }

    @Test
    void inventoryRouteUsesLoadBalancedUri() {
        Route route = routeById("inventory-service");
        assertThat(route.getUri()).isEqualTo(URI.create("lb://inventory-service"));
    }

    @Test
    void eurekaRoutesStillPresent() {
        Route web = routeById("discovery-server");
        assertThat(web.getUri()).isEqualTo(URI.create("http://localhost:8761"));
        assertThat(resolveRouteId("/eureka/web")).isEqualTo("discovery-server");

        Route statics = routeById("discovery-server-static");
        assertThat(statics.getUri()).isEqualTo(URI.create("http://localhost:8761"));
        assertThat(resolveRouteId("/eureka/css/wro.css")).isEqualTo("discovery-server-static");
    }

    @Test
    void preflightFromPosOriginReturnsExactlyOneAllowOrigin() {
        webTestClient.options()
                .uri("/api/order")
                .header(HttpHeaders.ORIGIN, POS_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        values -> assertThat(values).containsExactly(POS_ORIGIN))
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        methods -> assertThat(methods).contains("POST"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        headers -> assertThat(headers.toLowerCase()).contains("authorization"));
        assertThat(lastStubRequest.get()).isNull();
    }

    @Test
    void preflightFromSecondConfiguredOriginIsAllowed() {
        webTestClient.options()
                .uri("/api/inventory")
                .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5173");
    }

    @Test
    void actualRequestFromPosOriginExposesHeadersAndDedupesAllowOrigin() {
        webTestClient.get()
                .uri("/api/inventory?skuCode=268341-016-L&storeId=1969")
                .header(HttpHeaders.ORIGIN, POS_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        values -> assertThat(values).containsExactly(POS_ORIGIN))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        exposed -> assertThat(exposed).contains(HttpHeaders.LOCATION, HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() {
        webTestClient.options()
                .uri("/api/order")
                .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void actualRequestFromDisallowedOriginIsRejected() {
        webTestClient.get()
                .uri("/api/inventory?skuCode=268341-016-L")
                .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat(lastStubRequest.get()).isNull();
    }

    private Route routeById(String id) {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        return routes.stream()
                .filter(route -> route.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("route not found: " + id));
    }

    private String resolveRouteId(String path) {
        return routeLocator.getRoutes()
                .concatMap(route -> {
                    MockServerWebExchange exchange =
                            MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.GET, path));
                    return Flux.from(route.getPredicate().apply(exchange))
                            .filter(Boolean::booleanValue)
                            .map(matched -> route.getId());
                })
                .next()
                .block();
    }

    private static HttpServer startInventoryStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String query = exchange.getRequestURI().getRawQuery();
                lastStubRequest.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                        + (query == null ? "" : "?" + query));
                byte[] body = "{\"skuCode\":\"268341-016-L\",\"storeId\":\"1969\",\"onHand\":4}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("X-Stub", "inventory");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", POS_ORIGIN);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
