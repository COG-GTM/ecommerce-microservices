package com.ibatulanand.apigateway.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * Replaces the Keycloak-backed decoder so the security filter chain can be exercised without an
 * authorization server. {@link TestFixtures#VALID_TOKEN} is the only token that decodes.
 */
@TestConfiguration
public class StubJwtDecoderConfiguration {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return token ->
                TestFixtures.VALID_TOKEN.equals(token)
                        ? Mono.just(TestFixtures.jwt(token))
                        : Mono.error(new BadJwtException("Unknown token"));
    }
}
