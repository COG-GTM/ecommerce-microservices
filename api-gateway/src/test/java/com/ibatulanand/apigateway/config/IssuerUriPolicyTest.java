package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuerUriPolicyTest {

    private static final String PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    @Test
    void acceptsHttpsIssuer() {
        assertThatCode(() -> IssuerUriPolicy.validate(PROPERTY,
                "https://auth.example.com/realms/spring-boot-microservices-realm", false))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsPlainHttpOnLoopback() {
        assertThatCode(() -> IssuerUriPolicy.validate(PROPERTY,
                "http://localhost:8181/realms/spring-boot-microservices-realm", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> IssuerUriPolicy.validate(PROPERTY, "http://127.0.0.1:8080/realms/r", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> IssuerUriPolicy.validate(PROPERTY, "http://[::1]:8080/realms/r", false))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPlainHttpOnNonLoopbackHost() {
        assertThatThrownBy(() -> IssuerUriPolicy.validate(PROPERTY,
                "http://keycloak:8080/realms/spring-boot-microservices-realm", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PROPERTY);
        assertThatThrownBy(() -> IssuerUriPolicy.validate(PROPERTY, "http://10.0.0.5/realms/r", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> IssuerUriPolicy.validate(PROPERTY, "http://localhost.evil.com/realms/r", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsPlainHttpOnlyWithExplicitOptIn() {
        assertThatCode(() -> IssuerUriPolicy.validate(PROPERTY,
                "http://keycloak:8080/realms/spring-boot-microservices-realm", true))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonHttpSchemesEvenWithOptIn() {
        assertThatThrownBy(() -> IssuerUriPolicy.validate(PROPERTY, "ftp://keycloak/realms/r", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ignoresUnsetUri() {
        assertThatCode(() -> IssuerUriPolicy.validate(PROPERTY, "", false)).doesNotThrowAnyException();
    }
}
