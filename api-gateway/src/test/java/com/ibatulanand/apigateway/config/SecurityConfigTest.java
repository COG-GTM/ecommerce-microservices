package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void jwtValidatorAcceptsConfiguredIssuerAndRejectsDifferentIssuer() {
        String expectedIssuer = "http://localhost:8080/realms/spring-boot-microservices-realm";
        assertThat(SecurityConfig.jwtValidator(expectedIssuer).validate(jwt(expectedIssuer)).hasErrors()).isFalse();
        assertThat(SecurityConfig.jwtValidator(expectedIssuer)
                .validate(jwt("http://localhost:8181/realms/spring-boot-microservices-realm")).hasErrors()).isTrue();
    }

    @Test
    void realmRoleConverterMapsRolesAndHandlesMissingRealmAccess() {
        KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithClaims(Map.of(
                "realm_access", Map.of("roles", List.of("pos-associate", "pos-manager")))));
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_pos-associate", "ROLE_pos-manager");
        assertThat(converter.convert(jwtWithClaims(Map.of()))).isEmpty();
        assertThat(converter.convert(jwtWithClaims(Map.of("realm_access", "malformed")))).isEmpty();
    }

    private static Jwt jwt(String issuer) {
        return Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("iss", issuer)
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue("test")
                .header("alg", "none")
                .claims(current -> current.putAll(claims))
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
