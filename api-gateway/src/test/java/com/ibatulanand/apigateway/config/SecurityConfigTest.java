package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "management.tracing.enabled=false"
})
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void anonymousEurekaRequestsAreRejected() {
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
            webTestClient.method(method).uri("/eureka/apps/PRODUCT-SERVICE")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
        webTestClient.get().uri("/eureka/web").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void authenticatedEurekaRequestsAreForbidden() {
        WebTestClient client = webTestClient.mutateWith(mockJwt());
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
            client.method(method).uri("/eureka/apps/PRODUCT-SERVICE/instance-1")
                    .exchange()
                    .expectStatus().isForbidden();
        }
        client.get().uri("/eureka/web").exchange().expectStatus().isForbidden();
    }

    @Test
    void anonymousApiRequestsStillRequireJwt() {
        webTestClient.get().uri("/api/product").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void noGatewayRouteForwardsToTheDiscoveryServer() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).extracting(Route::getId).containsExactlyInAnyOrder("product-service", "order-service");
        assertThat(routes).allSatisfy(route -> assertThat(route.getUri().getPort()).isNotEqualTo(8761));
    }
}
