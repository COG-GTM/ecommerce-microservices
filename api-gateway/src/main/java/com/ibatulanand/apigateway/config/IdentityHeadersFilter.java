package com.ibatulanand.apigateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

    public static final String ASSOCIATE_ID_HEADER = "X-Associate-Id";
    public static final String STORE_ID_HEADER = "X-Store-Id";
    public static final String REGISTER_ID_HEADER = "X-Register-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange strippedExchange = mutateHeaders(exchange, null);
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> mutateHeaders(strippedExchange, context.getAuthentication()))
                .defaultIfEmpty(strippedExchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange mutateHeaders(ServerWebExchange exchange, Authentication authentication) {
        return exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove(ASSOCIATE_ID_HEADER);
            headers.remove(STORE_ID_HEADER);
            headers.remove(REGISTER_ID_HEADER);
            headers.remove(USER_ROLES_HEADER);

            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                Jwt jwt = jwtAuthentication.getToken();
                setClaimHeader(headers, ASSOCIATE_ID_HEADER, jwt.getClaim("associate_id"));
                setClaimHeader(headers, STORE_ID_HEADER, jwt.getClaim("store_id"));
                setClaimHeader(headers, REGISTER_ID_HEADER, jwt.getClaim("register_id"));
                Object realmAccessClaim = jwt.getClaims().get("realm_access");
                if (realmAccessClaim instanceof Map<?, ?> realmAccess
                        && realmAccess.get("roles") instanceof Collection<?> roles) {
                    String roleNames = roles.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .collect(Collectors.joining(","));
                    if (!roleNames.isBlank()) {
                        headers.set(USER_ROLES_HEADER, roleNames);
                    }
                }
            }
        })).build();
    }

    private void setClaimHeader(HttpHeaders headers, String headerName, Object claim) {
        if (claim != null && !claim.toString().isBlank()) {
            headers.set(headerName, claim.toString());
        }
    }
}
