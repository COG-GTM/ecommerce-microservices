package com.ibatulanand.apigateway.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;

/**
 * Accepts only tokens issued to one of the configured clients (the {@code azp} claim).
 */
public class AuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AZP = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The token was not issued to an allowed client", null);

    private final Set<String> allowedClients;

    public AuthorizedPartyValidator(List<String> allowedClients) {
        if (allowedClients == null || allowedClients.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed client must be configured");
        }
        this.allowedClients = Set.copyOf(allowedClients);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String azp = jwt.getClaimAsString("azp");
        if (azp != null && allowedClients.contains(azp)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AZP);
    }
}
