package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void eurekaEndpoint_isPermittedWithoutAuth() {
        // /eureka/** is permitAll, so it must not be rejected by security (no 401).
        webTestClient.get().uri("/eureka/web")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void protectedEndpoint_requiresAuth() {
        webTestClient.get().uri("/api/product")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
