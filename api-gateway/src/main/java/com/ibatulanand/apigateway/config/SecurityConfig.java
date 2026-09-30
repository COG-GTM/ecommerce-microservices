package com.ibatulanand.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String PRODUCT_ADMIN_ROLE = "product-admin";

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange ->
                        exchange.pathMatchers("/eureka/**")
                                .permitAll()
                                .pathMatchers(HttpMethod.GET, "/api/product", "/api/product/**")
                                .authenticated()
                                .pathMatchers("/api/product", "/api/product/**")
                                .hasRole(PRODUCT_ADMIN_ROLE)
                                .anyExchange()
                                .authenticated())
                .oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return serverHttpSecurity.build();
    }

    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
