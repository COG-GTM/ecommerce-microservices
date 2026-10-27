package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Boots the gateway with discovery and tracing disabled (no external dependencies) and verifies
 * the security filter chain and route table: anonymous API calls are rejected, /eureka/** is public,
 * and authenticated calls are routed (503 because no product/order instance is registered).
 */
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "management.tracing.enabled=false"})
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void anonymousProductRequestIsUnauthorized() {
        webTestClient.get().uri("/api/product").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void anonymousOrderRequestIsUnauthorized() {
        webTestClient.post().uri("/api/order").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void eurekaPathsArePublicAndProxied() {
        webTestClient.get().uri("/eureka/web").exchange()
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403));
    }

    @Test
    void authenticatedProductRequestIsRoutedToLoadBalancedService() {
        webTestClient.mutateWith(mockJwt()).get().uri("/api/product").exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void csrfIsDisabledForAuthenticatedPosts() {
        webTestClient.mutateWith(mockJwt()).post().uri("/api/order").exchange()
                .expectStatus().isEqualTo(503);
    }
}
