package com.ibatulanand.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false"
})
@AutoConfigureWebTestClient
class ApiGatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Test
    void inventoryServiceRouteIsLoadBalanced() {
        Route route = routeLocator.getRoutes()
                .filter(r -> "inventory-service".equals(r.getId()))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create("lb://inventory-service"));
    }

    @Test
    void inventoryRequestIsRoutedToInventoryService() {
        // No inventory-service instance is registered, so a matched route yields 503 rather than 404.
        webTestClient.mutateWith(mockJwt())
                .get()
                .uri("/api/inventory?skuCode=iphone_13")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void unknownPathIsNotRouted() {
        webTestClient.mutateWith(mockJwt())
                .get()
                .uri("/api/unknown")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void inventoryRequestWithoutTokenIsRejected() {
        webTestClient.get()
                .uri("/api/inventory?skuCode=iphone_13")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void corsPreflightForPosWebappIsAllowed() {
        webTestClient.options()
                .uri("http://localhost:8181/api/inventory?skuCode=iphone_13")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void localIssuerUriPointsAtKeycloak() {
        assertThat(issuerUri).isEqualTo("http://localhost:8080/realms/spring-boot-microservices-realm");
    }
}
