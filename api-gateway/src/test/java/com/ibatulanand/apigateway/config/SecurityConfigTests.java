package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    private String tokenWithRoles(String token, String... roles) {
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .claim("azp", "spring-cloud-client")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(reactiveJwtDecoder.decode(token)).thenReturn(Mono.just(jwt));
        return token;
    }

    private int status(WebTestClient.RequestHeadersSpec<?> request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value();
    }

    @Test
    void apiRoutesRequireAuthentication() {
        webTestClient.get().uri("/api/product").exchange().expectStatus().isUnauthorized();
        webTestClient.post().uri("/api/product").exchange().expectStatus().isUnauthorized();
        webTestClient.post().uri("/api/order").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void invalidTokenIsRejected() {
        assertThat(status(webTestClient.get().uri("/api/product"), "forged"))
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void tokenWithoutApiRolesIsForbiddenOnEveryApiRoute() {
        String token = tokenWithRoles("no-roles", "default-roles-spring-boot-microservices-realm");

        assertThat(status(webTestClient.get().uri("/api/product"), token)).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(status(webTestClient.post().uri("/api/product"), token)).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(status(webTestClient.post().uri("/api/order"), token)).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void productReadRoleOnlyAllowsListingProducts() {
        String token = tokenWithRoles("reader", SecurityConfig.PRODUCT_READ);

        assertThat(status(webTestClient.get().uri("/api/product"), token))
                .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        assertThat(status(webTestClient.post().uri("/api/product"), token)).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(status(webTestClient.post().uri("/api/order"), token)).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void writeRolesAllowTheirRoutes() {
        String productWriter = tokenWithRoles("product-writer", SecurityConfig.PRODUCT_WRITE);
        String orderWriter = tokenWithRoles("order-writer", SecurityConfig.ORDER_WRITE);

        assertThat(status(webTestClient.post().uri("/api/product"), productWriter))
                .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        assertThat(status(webTestClient.post().uri("/api/order"), productWriter)).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(status(webTestClient.post().uri("/api/order"), orderWriter))
                .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        assertThat(status(webTestClient.post().uri("/api/product"), orderWriter)).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void unmappedApiMethodsAreDenied() {
        String token = tokenWithRoles("all-roles",
                SecurityConfig.PRODUCT_READ, SecurityConfig.PRODUCT_WRITE, SecurityConfig.ORDER_WRITE);

        assertThat(status(webTestClient.delete().uri("/api/product"), token)).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(status(webTestClient.get().uri("/api/order"), token)).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void eurekaIsStillPermitted() {
        webTestClient.get().uri("/eureka/web")
                .exchange()
                .expectStatus().value(code -> assertThat(code).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }
}
