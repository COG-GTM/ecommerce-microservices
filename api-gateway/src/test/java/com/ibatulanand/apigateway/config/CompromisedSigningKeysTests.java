package com.ibatulanand.apigateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompromisedSigningKeysTests {

    private final KeyPair realmKey = rsaKeyPair();

    @Test
    void verifiesTokenSignedWithTrustedKey() throws Exception {
        DefaultJWTProcessor<SecurityContext> processor = processor(new CompromisedSigningKeys(CompromisedSigningKeys.KNOWN));

        assertThat(processor.process(sign(realmKey), null).getSubject()).isEqualTo("service-account");
    }

    @Test
    void rejectsTokenSignedWithCompromisedKey() {
        String leaked = CompromisedSigningKeys.fingerprint(realmKey.getPublic().getEncoded());
        DefaultJWTProcessor<SecurityContext> processor = processor(new CompromisedSigningKeys(Set.of(leaked)));

        assertThatThrownBy(() -> processor.process(sign(realmKey), null)).isInstanceOf(BadJOSEException.class);
    }

    @Test
    void keepsAVerificationKeySelectorWithTheSameAlgorithms() {
        DefaultJWTProcessor<SecurityContext> processor = processor(new CompromisedSigningKeys(CompromisedSigningKeys.KNOWN));

        assertThat(processor.getJWSKeySelector()).isInstanceOf(JWSVerificationKeySelector.class);
        JWSVerificationKeySelector<SecurityContext> selector =
                (JWSVerificationKeySelector<SecurityContext>) processor.getJWSKeySelector();
        assertThat(selector.isAllowed(JWSAlgorithm.RS256)).isTrue();
        assertThat(selector.isAllowed(JWSAlgorithm.HS256)).isFalse();
    }

    @Test
    void keepsEveryAlgorithmTheDecoderAllows() {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(Set.of(JWSAlgorithm.RS256, JWSAlgorithm.PS256, JWSAlgorithm.ES256),
                new ImmutableJWKSet<>(new JWKSet())));
        new CompromisedSigningKeys(CompromisedSigningKeys.KNOWN).apply(processor);

        JWSVerificationKeySelector<SecurityContext> selector =
                (JWSVerificationKeySelector<SecurityContext>) processor.getJWSKeySelector();
        assertThat(List.of(JWSAlgorithm.RS256, JWSAlgorithm.PS256, JWSAlgorithm.ES256)).allMatch(selector::isAllowed);
        assertThat(List.of(JWSAlgorithm.RS512, JWSAlgorithm.HS256)).noneMatch(selector::isAllowed);
    }

    @Test
    void refusesKeySelectorsItCannotWrap() {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector((header, context) -> List.of());

        assertThatThrownBy(() -> new CompromisedSigningKeys(CompromisedSigningKeys.KNOWN).apply(processor))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void knownListHoldsSha256Fingerprints() {
        assertThat(CompromisedSigningKeys.KNOWN).isNotEmpty().allMatch(fp -> fp.matches("[0-9a-f]{64}"));
        assertThat(new CompromisedSigningKeys(CompromisedSigningKeys.KNOWN).isCompromised(realmKey.getPublic())).isFalse();
    }

    private DefaultJWTProcessor<SecurityContext> processor(CompromisedSigningKeys compromisedKeys) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) realmKey.getPublic()).keyID("realm-key").build();
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(new JWKSet(jwk))));
        compromisedKeys.apply(processor);
        return processor;
    }

    private static SignedJWT sign(KeyPair keyPair) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("realm-key").build(),
                new JWTClaimsSet.Builder().subject("service-account").build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt;
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
