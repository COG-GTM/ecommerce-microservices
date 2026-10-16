package com.ibatulanand.apigateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "management.tracing.enabled=false"
        })
class SecurityConfigTest {

    private static final String ISSUER = "http://localhost:8080/realms/spring-boot-microservices-realm";

    private static final HttpServer PRODUCT_SERVICE = startProductServiceStub();

    @DynamicPropertySource
    static void productServiceInstance(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri",
                () -> "http://localhost:" + PRODUCT_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        PRODUCT_SERVICE.stop(0);
    }

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Test
    void associateTokenReachesProductService() {
        givenToken("associate-token", List.of("pos-associate", "offline_access"));

        webTestClient.get().uri("/api/product")
                .header(HttpHeaders.AUTHORIZATION, "Bearer associate-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void tokenWithoutPosAssociateIsForbidden() {
        givenToken("noaccess-token", List.of("offline_access", "uma_authorization"));

        webTestClient.get().uri("/api/product")
                .header(HttpHeaders.AUTHORIZATION, "Bearer noaccess-token")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void missingTokenIsUnauthorized() {
        webTestClient.get().uri("/api/product")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidTokenIsUnauthorized() {
        given(jwtDecoder.decode(anyString())).willReturn(Mono.error(new BadJwtException("bad issuer")));

        webTestClient.get().uri("/api/product")
                .header(HttpHeaders.AUTHORIZATION, "Bearer forged")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void corsPreflightIsOpenWithoutToken() {
        webTestClient.method(HttpMethod.OPTIONS).uri("/api/product")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void issuerMatchesKeycloakPublicHostname() {
        assertThat(issuerUri).isEqualTo(ISSUER);
        assertThat(jwkSetUri).isEqualTo(ISSUER + "/protocol/openid-connect/certs");
    }

    @Test
    void realmRolesMapToSpringRoles() {
        Jwt jwt = jwt("t", List.of("pos-associate", "pos-manager"));

        assertThat(SecurityConfig.realmRoleAuthorities(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_pos-associate", "ROLE_pos-manager");
    }

    @Test
    void missingRealmAccessMapsToNoRoles() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("svc").build();

        assertThat(SecurityConfig.realmRoleAuthorities(jwt)).isEmpty();
    }

    private void givenToken(String token, List<String> roles) {
        given(jwtDecoder.decode(token)).willReturn(Mono.just(jwt(token, roles)));
    }

    private static Jwt jwt(String token, List<String> roles) {
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("user")
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static HttpServer startProductServiceStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/product", exchange -> {
                byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
