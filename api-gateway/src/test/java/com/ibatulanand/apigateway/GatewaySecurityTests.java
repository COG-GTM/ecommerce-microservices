package com.ibatulanand.apigateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.apigateway.config.IdentityHeadersFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTests {

    private static final String ISSUER = "http://keycloak.test/realms/spring-boot-microservices-realm";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static HttpServer downstream;

    @LocalServerPort
    private int gatewayPort;

    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        if (downstream == null) {
            try {
                downstream = HttpServer.create(new InetSocketAddress(0), 0);
                downstream.createContext("/", exchange -> {
                    Map<String, Object> echo = new LinkedHashMap<>();
                    echo.put("method", exchange.getRequestMethod());
                    echo.put("path", exchange.getRequestURI().toString());
                    echo.put("xAssociateId", headerOrAbsent(exchange.getRequestHeaders(), IdentityHeadersFilter.ASSOCIATE_ID_HEADER));
                    echo.put("xStoreId", headerOrAbsent(exchange.getRequestHeaders(), IdentityHeadersFilter.STORE_ID_HEADER));
                    echo.put("xRegisterId", headerOrAbsent(exchange.getRequestHeaders(), IdentityHeadersFilter.REGISTER_ID_HEADER));
                    echo.put("xUserRoles", headerOrAbsent(exchange.getRequestHeaders(), IdentityHeadersFilter.USER_ROLES_HEADER));
                    byte[] body = OBJECT_MAPPER.writeValueAsBytes(echo);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
                downstream.start();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start the downstream test stub", exception);
            }
        }
        int port = downstream.getAddress().getPort();
        for (String service : List.of("product-service", "inventory-service", "order-service")) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
                    () -> "http://localhost:" + port);
        }
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) {
            downstream.stop(0);
        }
    }

    @BeforeEach
    void configureJwtDecoder() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return switch (token) {
                case "associate" -> Mono.just(jwt(token, List.of("pos-associate"),
                        Map.of("associate_id", "A-4471", "store_id", "1969", "register_id", "04")));
                case "manager" -> Mono.just(jwt(token, List.of("pos-associate", "pos-manager"),
                        Map.of("associate_id", "A-1002", "store_id", "1969", "register_id", "04")));
                case "admin" -> Mono.just(jwt(token, List.of("product-admin"), Map.of()));
                case "noRole" -> Mono.just(jwt(token,
                        List.of("default-roles-spring-boot-microservices-realm"), Map.of()));
                case "missing" -> Mono.just(jwt(token, List.of("pos-associate"),
                        Map.of("associate_id", "A-4471")));
                default -> Mono.error(new BadJwtException("Unrecognized test token"));
            };
        });
    }

    static Stream<Arguments> authorizationCases() {
        List<RequestCase> cases = List.of(
                new RequestCase("GET", "/api/product", Set.of("associate", "manager")),
                new RequestCase("GET", "/api/product/sku/268341-016-XS", Set.of("associate", "manager")),
                new RequestCase("GET", "/api/inventory?skuCode=268341-016-XS&storeId=1969", Set.of("associate", "manager")),
                new RequestCase("POST", "/api/order/quote", Set.of("associate", "manager")),
                new RequestCase("POST", "/api/order", Set.of("associate", "manager")),
                new RequestCase("POST", "/api/product", Set.of("admin")),
                new RequestCase("PUT", "/api/product/sku/268341-016-XS", Set.of("admin")),
                new RequestCase("DELETE", "/api/product/sku/268341-016-XS", Set.of("admin")),
                new RequestCase("GET", "/api/order", Set.of()),
                new RequestCase("GET", "/api/order/123", Set.of()),
                new RequestCase("POST", "/api/inventory", Set.of()),
                new RequestCase("GET", "/eureka/web", Set.of()),
                new RequestCase("GET", "/eureka/apps", Set.of()),
                new RequestCase("GET", "/actuator/prometheus", Set.of()));
        List<Arguments> arguments = new ArrayList<>();
        List<String> tokenKinds = java.util.Arrays.asList("associate", "manager", "admin", "noRole", null);
        for (RequestCase request : cases) {
            for (String tokenKind : tokenKinds) {
                int expected = tokenKind == null ? 401 : request.allowedRoles().contains(tokenKind) ? 200 : 403;
                arguments.add(Arguments.of(request.method(), request.path(), tokenKind, expected));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest
    @MethodSource("authorizationCases")
    void enforcesRoleMatrixAndRoutesRequests(String method, String path, String tokenKind, int expectedStatus) {
        WebTestClient.ResponseSpec response = send(method, path, tokenKind);
        if (expectedStatus == 200) {
            response.expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.method").isEqualTo(method)
                    .jsonPath("$.path").isEqualTo(path);
        } else {
            response.expectStatus().isEqualTo(expectedStatus);
        }
    }

    @Test
    void replacesSpoofedIdentityHeadersWithTokenIdentity() {
        send("GET", "/api/product", "associate")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.xAssociateId").isEqualTo("A-4471")
                .jsonPath("$.xStoreId").isEqualTo("1969")
                .jsonPath("$.xRegisterId").isEqualTo("04")
                .jsonPath("$.xUserRoles").isEqualTo("pos-associate");

        send("GET", "/api/product", "manager")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.xUserRoles").isEqualTo("pos-associate,pos-manager");
    }

    @Test
    void omitsIdentityHeadersWhenClaimsAreMissing() {
        send("GET", "/api/product", "missing")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.xAssociateId").isEqualTo("A-4471")
                .jsonPath("$.xStoreId").isEqualTo("<absent>")
                .jsonPath("$.xRegisterId").isEqualTo("<absent>");
    }

    @Test
    void allowsCorsPreflightOnlyFromConfiguredOrigins() {
        for (String origin : List.of("http://localhost:5173", "http://127.0.0.1:5173")) {
            webTestClient.options().uri("/api/order")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        }

        webTestClient.options().uri("/api/order")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void plainOptionsIsNotTreatedAsCorsPreflight() {
        webTestClient.options().uri("/api/product")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void corsHeadersArePresentOnUnauthorizedApiResponses() {
        webTestClient.get().uri("/api/product")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void exposesOnlyTheThreeServiceRoutes() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        assertThat(routes).extracting(Route::getId)
                .containsExactlyInAnyOrder("product-service", "inventory-service", "order-service");
        assertThat(routes).allSatisfy(route -> {
            assertThat(route.getUri().getPort()).isNotEqualTo(8761);
            assertThat(route.getUri().getHost()).doesNotContainIgnoringCase("discovery", "eureka");
        });
    }

    private WebTestClient.ResponseSpec send(String method, String path, String tokenKind) {
        WebTestClient.RequestBodySpec request = webTestClient.method(HttpMethod.valueOf(method))
                .uri(path);
        if (tokenKind != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenKind);
        }
        if (method.equals("POST") || method.equals("PUT")) {
            request.contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("request", "body"));
        }
        request.header(IdentityHeadersFilter.ASSOCIATE_ID_HEADER, "EVIL")
                .header(IdentityHeadersFilter.STORE_ID_HEADER, "9999")
                .header(IdentityHeadersFilter.REGISTER_ID_HEADER, "99")
                .header(IdentityHeadersFilter.USER_ROLES_HEADER, "product-admin");
        return request.exchange();
    }

    private static Jwt jwt(String value, List<String> roles, Map<String, Object> claims) {
        return Jwt.withTokenValue(value)
                .header("alg", "none")
                .claim("iss", ISSUER)
                .claim("realm_access", Map.of("roles", roles))
                .claims(current -> current.putAll(claims))
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static String headerOrAbsent(com.sun.net.httpserver.Headers headers, String name) {
        String value = headers.getFirst(name);
        return value == null ? "<absent>" : value;
    }

    private record RequestCase(String method, String path, Set<String> allowedRoles) {
    }
}
