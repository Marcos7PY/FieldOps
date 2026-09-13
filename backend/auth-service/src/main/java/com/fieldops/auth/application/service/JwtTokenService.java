package com.fieldops.auth.application.service;

import com.fieldops.auth.domain.exception.InvalidTokenException;
import com.fieldops.auth.domain.model.RefreshToken;
import com.fieldops.auth.domain.model.Role;
import com.fieldops.auth.domain.model.User;
import com.fieldops.auth.infrastructure.config.JwtProperties;
import com.fieldops.auth.infrastructure.config.RsaKeyProvider;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService implements TokenService {

    private final JwtProperties properties;
    private final RsaKeyProvider rsaKeyProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public JwtTokenService(
            JwtProperties properties,
            RsaKeyProvider rsaKeyProvider,
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.properties = properties;
        this.rsaKeyProvider = rsaKeyProvider;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public String generateAccessToken(User user) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(properties.getAccessTokenExpirationSeconds());

            List<String> roleNames = user.getRoles().stream()
                    .map(Role::getName)
                    .toList();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("fieldops-auth")
                    .subject(user.getUsername())
                    .claim("userId", user.getId())
                    .claim("roles", roleNames)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKeyProvider.getKeyId())
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(rsaKeyProvider.getPrivateKey()));

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT access token", e);
        }
    }

    @Override
    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "");
        String hash = sha256Hex(rawToken);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(properties.getRefreshTokenExpirationDays());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hash);
        refreshToken.setUser(user);
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(expiresAt);
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Override
    @Transactional
    public RefreshToken verifyAndGetRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        String hash = sha256Hex(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (token.isRevoked() || token.isExpired()) {
            throw new InvalidTokenException("Refresh token is revoked or expired");
        }
        return token;
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}