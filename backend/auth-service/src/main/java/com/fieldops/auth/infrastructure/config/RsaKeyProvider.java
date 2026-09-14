package com.fieldops.auth.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class RsaKeyProvider implements InitializingBean {

    private final JwtProperties properties;
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private RSAKey rsaJwk;
    private JWKSet publicJwkSet;

    public RsaKeyProvider(JwtProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadOrGenerateKeys();
    }

    public synchronized void loadOrGenerateKeys() {
        Path keyDir = Paths.get(properties.getKeyPath()).toAbsolutePath().normalize();
        Path privFile = keyDir.resolve("private.pem");
        Path pubFile = keyDir.resolve("public.pem");

        try {
            if (Files.exists(privFile) && Files.exists(pubFile)) {
                loadKeys(privFile, pubFile);
            } else {
                generateAndSaveKeys(keyDir, privFile, pubFile);
            }

            this.rsaJwk = new RSAKey.Builder(this.publicKey)
                    .privateKey(this.privateKey)
                    .keyID(properties.getKeyId())
                    .build();

            this.publicJwkSet = new JWKSet(this.rsaJwk.toPublicJWK());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RSA keys from " + keyDir, e);
        }
    }

    private void loadKeys(Path privFile, Path pubFile) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String privContent = Files.readString(privFile);
        String pubContent = Files.readString(pubFile);

        KeyFactory kf = KeyFactory.getInstance("RSA");

        byte[] privDer = extractDer(privContent, "PRIVATE KEY", "RSA PRIVATE KEY");
        if (privContent.contains("BEGIN RSA PRIVATE KEY")) {
            privDer = pkcs1ToPkcs8(privDer);
        }
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privDer);
        this.privateKey = (RSAPrivateKey) kf.generatePrivate(privSpec);

        byte[] pubDer = extractDer(pubContent, "PUBLIC KEY");
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubDer);
        this.publicKey = (RSAPublicKey) kf.generatePublic(pubSpec);
    }

    private void generateAndSaveKeys(Path keyDir, Path privFile, Path pubFile) throws IOException, NoSuchAlgorithmException {
        Files.createDirectories(keyDir);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        this.publicKey = (RSAPublicKey) pair.getPublic();
        this.privateKey = (RSAPrivateKey) pair.getPrivate();

        Base64.Encoder encoder = Base64.getMimeEncoder(64, new byte[]{'\n'});

        String privPem = "-----BEGIN PRIVATE KEY-----\n" +
                encoder.encodeToString(this.privateKey.getEncoded()) +
                "\n-----END PRIVATE KEY-----\n";

        String pubPem = "-----BEGIN PUBLIC KEY-----\n" +
                encoder.encodeToString(this.publicKey.getEncoded()) +
                "\n-----END PUBLIC KEY-----\n";

        Files.writeString(privFile, privPem);
        try {
            java.nio.file.attribute.PosixFileAttributeView view = Files.getFileAttributeView(
                    privFile, java.nio.file.attribute.PosixFileAttributeView.class);
            if (view != null) {
                Files.setPosixFilePermissions(privFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // Non-POSIX filesystem (e.g. Windows NTFS)
        }
        Files.writeString(pubFile, pubPem);
    }

    private byte[] extractDer(String pem, String... markers) {
        String cleaned = pem;
        for (String marker : markers) {
            cleaned = cleaned.replace("-----BEGIN " + marker + "-----", "")
                    .replace("-----END " + marker + "-----", "");
        }
        cleaned = cleaned.replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }

    private byte[] pkcs1ToPkcs8(byte[] pkcs1Bytes) {
        int pkcs1Length = pkcs1Bytes.length;
        int totalLength = pkcs1Length + 22;
        byte[] pkcs8Header = new byte[]{
                0x30, (byte) 0x82, (byte) ((totalLength >> 8) & 0xff), (byte) (totalLength & 0xff),
                0x02, 0x01, 0x00,
                0x30, 0x0d,
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00,
                0x04, (byte) 0x82, (byte) ((pkcs1Length >> 8) & 0xff), (byte) (pkcs1Length & 0xff)
        };
        byte[] pkcs8Bytes = new byte[pkcs8Header.length + pkcs1Bytes.length];
        System.arraycopy(pkcs8Header, 0, pkcs8Bytes, 0, pkcs8Header.length);
        System.arraycopy(pkcs1Bytes, 0, pkcs8Bytes, pkcs8Header.length, pkcs1Bytes.length);
        return pkcs8Bytes;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getKeyId() {
        return properties.getKeyId();
    }

    public RSAKey getRsaJwk() {
        return rsaJwk;
    }

    public JWKSet getPublicJwkSet() {
        return publicJwkSet;
    }
}
