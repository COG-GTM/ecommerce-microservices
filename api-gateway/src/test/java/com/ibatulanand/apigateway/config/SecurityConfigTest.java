package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@WebFluxTest(properties = "eureka.client.enabled=false")
@Import({SecurityConfig.class, SecurityConfigTest.ProductStubController.class})
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void createProductWithoutTokenIsUnauthorized() {
        postProduct(null).expectStatus().isUnauthorized();
    }

    @Test
    void createProductWithoutAdminRoleIsForbidden() {
        givenToken("customer-token", List.of("default-roles-spring-boot-microservices-realm", "offline_access"));
        postProduct("customer-token").expectStatus().isForbidden();
    }

    @Test
    void createProductWithoutRealmAccessClaimIsForbidden() {
        givenToken("service-token", null);
        postProduct("service-token").expectStatus().isForbidden();
    }

    @Test
    void createProductWithAdminRoleIsAllowed() {
        givenToken("admin-token", List.of("product-admin"));
        postProduct("admin-token").expectStatus().isCreated();
    }

    @Test
    void listProductsRequiresAuthenticationOnly() {
        givenToken("customer-token", List.of("offline_access"));
        webTestClient.get().uri("/api/product").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/product").headers(h -> h.setBearerAuth("customer-token"))
                .exchange().expectStatus().isOk();
    }

    private WebTestClient.ResponseSpec postProduct(String token) {
        WebTestClient.RequestBodySpec request = webTestClient.post().uri("/api/product")
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.headers(h -> h.setBearerAuth(token));
        }
        return request.bodyValue("{\"name\":\"x\"}").exchange();
    }

    private void givenToken(String token, List<String> realmRoles) {
        Jwt.Builder builder = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (realmRoles != null) {
            builder.claim("realm_access", Map.of("roles", realmRoles));
        }
        when(jwtDecoder.decode(token)).thenReturn(Mono.just(builder.build()));
    }

    @RestController
    @RequestMapping("/api/product")
    static class ProductStubController {

        @GetMapping
        Mono<String> list() {
            return Mono.just("[]");
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        Mono<Void> create() {
            return Mono.empty();
        }
    }
}
