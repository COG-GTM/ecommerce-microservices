package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class IssuerConfigTest {

    @Test
    void resolvesBrowserIssuerAndJwkSetDefaults() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new PropertiesPropertySourceLoader()
                .load("gateway-application", new FileSystemResource("src/main/resources/application.properties"))
                .forEach(environment.getPropertySources()::addLast);

        assertThat(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .isEqualTo("http://localhost:8080/realms/spring-boot-microservices-realm");
        assertThat(environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                .isEqualTo("http://localhost:8080/realms/spring-boot-microservices-realm/protocol/openid-connect/certs");
    }
}
