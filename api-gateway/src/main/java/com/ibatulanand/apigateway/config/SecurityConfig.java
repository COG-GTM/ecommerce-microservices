package com.ibatulanand.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.security.public-paths:}")
    private String[] publicPaths;

    @Value("${app.security.public-get-paths:}")
    private String[] publicGetPaths;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> {
                    // Internal sale writer: only till-service (via Eureka) may call it, never through the gateway.
                    exchange.pathMatchers("/api/order/sale").denyAll();
                    if (publicPaths.length > 0) {
                        exchange.pathMatchers(publicPaths).permitAll();
                    }
                    if (publicGetPaths.length > 0) {
                        exchange.pathMatchers(HttpMethod.GET, publicGetPaths).permitAll();
                    }
                    exchange.pathMatchers("/actuator/health/**").permitAll();
                    exchange.pathMatchers(HttpMethod.OPTIONS, "/**")
                                .permitAll()
                                .pathMatchers("/eureka/**")
                                .permitAll()
                                .anyExchange()
                                .authenticated();
                })
                .oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec.jwt(Customizer.withDefaults()));
        return serverHttpSecurity.build();
    }
}
