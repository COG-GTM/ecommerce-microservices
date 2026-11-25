package com.ibatulanand.apigateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * Wraps the processor's key selector so compromised keys are never used for verification. The wrapper stays a
     * {@link JWSVerificationKeySelector}, which Spring Security's {@code NimbusReactiveJwtDecoder} requires.
     */
    public <C extends SecurityContext> void apply(ConfigurableJWTProcessor<C> processor) {
        if (!(processor.getJWSKeySelector() instanceof JWSVerificationKeySelector<C> selector)) {
            throw new IllegalStateException("Unsupported JWS key selector " + processor.getJWSKeySelector());
        }
        processor.setJWSKeySelector(new TrustedKeySelector<>(selector));
    }

    private final class TrustedKeySelector<C extends SecurityContext> extends JWSVerificationKeySelector<C> {

        private TrustedKeySelector(JWSVerificationKeySelector<C> delegate) {
            super(allowedAlgorithms(delegate), delegate.getJWKSource());
        }

        @Override
        public List<Key> selectJWSKeys(JWSHeader header, C context) throws KeySourceException {
            List<Key> keys = super.selectJWSKeys(header, context);
            List<Key> trusted = keys.stream().filter(key -> !isCompromised(key)).toList();
            if (trusted.size() < keys.size()) {
                log.error("Rejected a token signed for kid={} with a compromised realm signing key; "
                        + "rotate the Keycloak realm keys", header.getKeyID());
            }
            return trusted;
        }
    }

    private static Set<JWSAlgorithm> allowedAlgorithms(JWSVerificationKeySelector<?> selector) {
        Set<JWSAlgorithm> allowed = Stream.of(JWSAlgorithm.Family.RSA, JWSAlgorithm.Family.EC, JWSAlgorithm.Family.HMAC_SHA)
                .flatMap(Set::stream)
                .filter(selector::isAllowed)
                .collect(Collectors.toUnmodifiableSet());
        if (allowed.isEmpty()) {
            throw new IllegalStateException("JWS key selector allows no RSA, EC or HMAC algorithm");
        }
        return allowed;
    }

    static String fingerprint(byte[] encodedKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encodedKey));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
