package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class SecurityConfigTests {

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        when(reactiveJwtDecoder.decode(anyString()))
                .thenReturn(Mono.error(new BadJwtException("invalid token")));
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
                .apply(SecurityMockServerConfigurers.springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void eurekaPathIsPermittedWithoutAuthentication() {
        webTestClient.get().uri("/eureka/web")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void eurekaWildcardPathIsPermittedWithoutAuthentication() {
        webTestClient.get().uri("/eureka/css/style.css")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void productRouteReturnsUnauthorizedWithoutJwt() {
        webTestClient.get().uri("/api/product")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void orderRouteReturnsUnauthorizedWithoutJwt() {
        webTestClient.post().uri("/api/order")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unknownPathReturnsUnauthorizedWithoutJwt() {
        webTestClient.get().uri("/some/other/path")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidBearerTokenReturnsUnauthorized() {
        webTestClient.get().uri("/api/product")
                .header("Authorization", "Bearer not-a-real-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void requestWithMockJwtIsAllowedThroughSecurity() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt())
                .get().uri("/api/product")
                .exchange()
                .expectStatus().value(status -> assertThat(status)
                        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
                        .isNotEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void requestWithMockJwtToUnknownPathIsNotUnauthorized() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt())
                .get().uri("/some/other/path")
                .exchange()
                .expectStatus().value(status -> assertThat(status)
                        .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
                        .isNotEqualTo(HttpStatus.FORBIDDEN.value()));
    }
}
