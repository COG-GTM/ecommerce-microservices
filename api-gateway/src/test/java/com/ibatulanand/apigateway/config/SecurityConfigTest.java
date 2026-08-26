package com.ibatulanand.apigateway.config;

import com.ibatulanand.apigateway.support.AbstractIntegrationTest;
import com.ibatulanand.apigateway.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void should_reject_request_when_routed_path_is_called_without_a_token() {
        webTestClient.get().uri("/api/product").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void should_permit_request_when_path_is_under_eureka() {
        HttpStatusCode status =
                webTestClient
                        .get()
                        .uri("/eureka/apps")
                        .exchange()
                        .returnResult(Void.class)
                        .getStatus();

        // The Eureka server itself is not running, so the gateway cannot proxy the call; all that
        // matters here is that security let the unauthenticated request through.
        assertThat(status).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void should_pass_security_filter_chain_when_request_carries_a_valid_bearer_token() {
        webTestClient
                .get()
                .uri("/not-a-configured-route")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestFixtures.VALID_TOKEN)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void should_reject_request_when_bearer_token_cannot_be_decoded() {
        webTestClient
                .get()
                .uri("/api/product")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
