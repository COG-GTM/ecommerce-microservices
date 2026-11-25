package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false"
})
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void eurekaPathsRequireAuthentication() {
        webTestClient.get().uri("/eureka/web").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/eureka/apps").exchange().expectStatus().isUnauthorized();
        webTestClient.post().uri("/eureka/apps/ORDER-SERVICE").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void eurekaIsNotRoutedThroughTheGateway() {
        webTestClient.mutateWith(mockJwt()).get().uri("/eureka/apps").exchange().expectStatus().isNotFound();
        webTestClient.mutateWith(mockJwt()).get().uri("/eureka/web").exchange().expectStatus().isNotFound();
    }
}
