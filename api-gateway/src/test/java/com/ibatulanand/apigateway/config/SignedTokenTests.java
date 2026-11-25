package com.ibatulanand.apigateway.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Sends RS256 tokens through the production Nimbus decoder and the gateway filter chain, against a JWKS
 * that still publishes a leaked key next to the current realm key.
 */
@SpringBootTest
class SignedTokenTests {

    private static final String ISSUER = "http://keycloak.test/realms/spring-boot-microservices-realm";

    private static final RSAKey REALM_KEY = rsaKey("realm-key");
    private static final RSAKey LEAKED_KEY = rsaKey("leaked-key");

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() throws JOSEException {
        String jwks = new JWKSet(List.of(REALM_KEY.toPublicJWK(), LEAKED_KEY.toPublicJWK())).toString();
        WebClient keycloak = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jwks)
                        .build()))
                .build();
        CompromisedSigningKeys compromisedKeys = new CompromisedSigningKeys(
                Set.of(CompromisedSigningKeys.fingerprint(LEAKED_KEY.toPublicKey().getEncoded())));
        NimbusReactiveJwtDecoder decoder = SecurityConfig.jwtDecoder(
                NimbusReactiveJwtDecoder.withJwkSetUri(ISSUER + "/protocol/openid-connect/certs").webClient(keycloak),
                compromisedKeys,
                SecurityConfig.jwtValidator(ISSUER, List.of("spring-cloud-client")));
        when(reactiveJwtDecoder.decode(anyString())).thenAnswer(call -> decoder.decode(call.getArgument(0)));
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void tokenSignedWithRealmKeyPassesTheGateway() throws JOSEException {
        String token = sign(REALM_KEY, "spring-cloud-client", SecurityConfig.PRODUCT_READ);

        assertThat(status(webTestClient.get().uri("/api/product"), token)).isNotIn(
                HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value(), HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    void tokenSignedWithLeakedKeyIsUnauthorized() throws JOSEException {
        String token = sign(LEAKED_KEY, "spring-cloud-client",
                SecurityConfig.PRODUCT_READ, SecurityConfig.PRODUCT_WRITE, SecurityConfig.ORDER_WRITE);

        assertThat(status(webTestClient.get().uri("/api/product"), token)).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(status(webTestClient.post().uri("/api/order"), token)).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void tokenForAnotherClientIsUnauthorized() throws JOSEException {
        String token = sign(REALM_KEY, "another-client", SecurityConfig.PRODUCT_READ);

        assertThat(status(webTestClient.get().uri("/api/product"), token)).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private int status(WebTestClient.RequestHeadersSpec<?> request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value();
    }

    private static String sign(RSAKey key, String authorizedParty, String... roles) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("service-account")
                .claim("azp", authorizedParty)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey rsaKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
