package com.ibatulanand.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
public class IssuerUriPolicy {

    public IssuerUriPolicy(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${gateway.security.allow-insecure-issuer:false}") boolean allowInsecureIssuer) {
        validate("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuerUri, allowInsecureIssuer);
        validate("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", jwkSetUri, allowInsecureIssuer);
    }

    static void validate(String property, String value, boolean allowInsecureIssuer) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return;
        }
        if (!"http".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(property + " must use https: " + value);
        }
        if (allowInsecureIssuer || isLoopback(uri.getHost())) {
            return;
        }
        throw new IllegalStateException(property + " must use https unless it points at a loopback host "
                + "or gateway.security.allow-insecure-issuer=true: " + value);
    }

    private static boolean isLoopback(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (!literal.matches("[0-9.]+") && !literal.contains(":")) {
            return false;
        }
        try {
            return InetAddress.getByName(literal).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
