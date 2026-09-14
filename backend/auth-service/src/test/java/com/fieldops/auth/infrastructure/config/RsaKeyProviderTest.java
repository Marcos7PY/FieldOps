package com.fieldops.auth.infrastructure.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class RsaKeyProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesKeysWhenMissingAndReloads() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setKeyPath(tempDir.toString());
        props.setKeyId("test-key-id");
        props.setAllowKeyGeneration(true);

        RsaKeyProvider provider = new RsaKeyProvider(props);
        provider.afterPropertiesSet();

        assertThat(Files.exists(tempDir.resolve("private.pem"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("public.pem"))).isTrue();
        assertThat(provider.getPublicKey()).isNotNull();
        assertThat(provider.getPrivateKey()).isNotNull();
        assertThat(provider.getKeyId()).isEqualTo("test-key-id");
        assertThat(provider.getPublicJwkSet().getKeys()).hasSize(1);

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(provider.getKeyId()).build(),
                new JWTClaimsSet.Builder().subject("testuser").expirationTime(new Date(System.currentTimeMillis() + 60000)).build()
        );
        signedJWT.sign(new RSASSASigner(provider.getPrivateKey()));

        SignedJWT parsed = SignedJWT.parse(signedJWT.serialize());
        boolean verified = parsed.verify(new RSASSAVerifier(provider.getPublicKey()));
        assertThat(verified).isTrue();

        RsaKeyProvider reloader = new RsaKeyProvider(props);
        reloader.afterPropertiesSet();
        assertThat(reloader.getPublicKey()).isEqualTo(provider.getPublicKey());
    }
}
