package com.ibatulanand.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .matchers(exchange -> exchange.getRequest().getMethod() == HttpMethod.OPTIONS
                                && exchange.getRequest().getHeaders().containsKey(HttpHeaders.ORIGIN)
                                && exchange.getRequest().getHeaders()
                                        .containsKey(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)
                                ? ServerWebExchangeMatcher.MatchResult.match()
                                : ServerWebExchangeMatcher.MatchResult.notMatch())
                        .permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/product", "/api/product/**")
                        .hasAnyRole("pos-associate", "pos-manager")
                        .pathMatchers(HttpMethod.GET, "/api/inventory", "/api/inventory/**")
                        .hasAnyRole("pos-associate", "pos-manager")
                        .pathMatchers(HttpMethod.POST, "/api/order/quote", "/api/order")
                        .hasAnyRole("pos-associate", "pos-manager")
                        .pathMatchers(HttpMethod.POST, "/api/product", "/api/product/**")
                        .hasRole("product-admin")
                        .pathMatchers(HttpMethod.PUT, "/api/product", "/api/product/**")
                        .hasRole("product-admin")
                        .pathMatchers(HttpMethod.DELETE, "/api/product", "/api/product/**")
                        .hasRole("product-admin")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter))))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtValidator(issuerUri));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> jwtValidator(String issuer) {
        return JwtValidators.createDefaultWithIssuer(issuer);
    }
}
