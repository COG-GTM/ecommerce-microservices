package com.ibatulanand.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String PRODUCT_READ = "product-read";
    public static final String PRODUCT_WRITE = "product-write";
    public static final String ORDER_WRITE = "order-write";

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange ->
                        exchange.pathMatchers("/eureka/**")
                                .permitAll()
                                .pathMatchers(HttpMethod.GET, "/api/product")
                                .hasRole(PRODUCT_READ)
                                .pathMatchers(HttpMethod.POST, "/api/product")
                                .hasRole(PRODUCT_WRITE)
                                .pathMatchers(HttpMethod.POST, "/api/order")
                                .hasRole(ORDER_WRITE)
                                .pathMatchers("/api/**")
                                .denyAll()
                                .anyExchange()
                                .authenticated())
                .oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec
                        .jwt(jwtSpec -> jwtSpec.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return serverHttpSecurity.build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${gateway.security.allowed-clients}") List<String> allowedClients) {
        OAuth2TokenValidator<Jwt> validator = jwtValidator(issuerUri, allowedClients);
        return new SupplierReactiveJwtDecoder(() -> {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri).build();
            decoder.setJwtValidator(validator);
            return decoder;
        });
    }

    static OAuth2TokenValidator<Jwt> jwtValidator(String issuerUri, List<String> allowedClients) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AuthorizedPartyValidator(allowedClients));
    }

    static ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
