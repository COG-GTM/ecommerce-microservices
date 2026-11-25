package com.ibatulanand.apigateway.config;

import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Refuses to verify tokens with signing keys that are known to be public, so a realm that still runs on
 * a leaked key fails closed instead of accepting tokens anyone could have signed.
 */
public final class CompromisedSigningKeys {

    private static final Logger log = LoggerFactory.getLogger(CompromisedSigningKeys.class);

    /**
     * SHA-256 of the SubjectPublicKeyInfo of the {@code rsa-generated} realm key whose private half was
     * committed in {@code realms/spring-boot-microservices-realm.json}.
     */
    public static final Set<String> KNOWN = Set.of(
            "faba0bdeaec4271970113bbb290abf9df71a99a535eb2ae957280e83b6c25f9d");

    private final Set<String> fingerprints;

    public CompromisedSigningKeys(Set<String> fingerprints) {
        this.fingerprints = Set.copyOf(fingerprints);
    }

    public boolean isCompromised(Key key) {
        byte[] encoded = key.getEncoded();
        return encoded != null && fingerprints.contains(fingerprint(encoded));
    }

    public <C extends SecurityContext> void apply(ConfigurableJWTProcessor<C> processor) {
        JWSKeySelector<C> delegate = processor.getJWSKeySelector();
        processor.setJWSKeySelector((header, context) -> {
            List<? extends Key> keys = delegate.selectJWSKeys(header, context);
            List<Key> trusted = keys.stream().filter(key -> !isCompromised(key)).map(Key.class::cast).toList();
            if (trusted.size() < keys.size()) {
                log.error("Rejected a token signed for kid={} with a compromised realm signing key; "
                        + "rotate the Keycloak realm keys", header.getKeyID());
            }
            return trusted;
        });
    }

    static String fingerprint(byte[] encodedKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encodedKey));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
