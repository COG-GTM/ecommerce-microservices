package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidationTests {

    private static final String ISSUER = "http://keycloak:8080/realms/spring-boot-microservices-realm";

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    private final OAuth2TokenValidator<Jwt> validator =
            SecurityConfig.jwtValidator(ISSUER, List.of("spring-cloud-client"));

    @Test
    void acceptsTokenIssuedToAllowedClient() {
        assertThat(validator.validate(jwt().claim("azp", "spring-cloud-client").build()).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenIssuedToOtherClient() {
        assertThat(validator.validate(jwt().claim("azp", "account-console").build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutAuthorizedParty() {
        assertThat(validator.validate(jwt().claim("sub", "someone").build()).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenFromOtherIssuer() {
        Jwt foreign = jwt().issuer("http://evil.example/realms/x").claim("azp", "spring-cloud-client").build();
        assertThat(validator.validate(foreign).hasErrors()).isTrue();
    }

    @Test
    void allowedClientsMustBeConfigured() {
        assertThatThrownBy(() -> new AuthorizedPartyValidator(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsRealmRolesToAuthorities() {
        Jwt token = jwt().claim("realm_access", Map.of("roles", List.of("product-read", "order-write"))).build();

        assertThat(new KeycloakRealmRoleConverter().convert(token))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_product-read", "ROLE_order-write");
    }

    @Test
    void tokenWithoutRealmRolesHasNoAuthorities() {
        assertThat(new KeycloakRealmRoleConverter().convert(jwt().claim("scope", "profile").build())).isEmpty();
    }
}
