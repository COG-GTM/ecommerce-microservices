package com.ibatulanand.apigateway.support;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Central place to build gateway test data, so a token change touches one file. */
public final class TestFixtures {

    public static final String VALID_TOKEN = "stub-access-token";

    private TestFixtures() {
    }

    public static Jwt jwt(String tokenValue) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .subject("test-user")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(1, ChronoUnit.HOURS))
                .claim("scope", "openid")
                .build();
    }
}
