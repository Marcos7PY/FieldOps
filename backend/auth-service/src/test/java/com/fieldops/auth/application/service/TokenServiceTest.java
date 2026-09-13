package com.fieldops.auth.application.service;

import com.fieldops.auth.domain.exception.InvalidTokenException;
import com.fieldops.auth.domain.model.RefreshToken;
import com.fieldops.auth.domain.model.Role;
import com.fieldops.auth.domain.model.User;
import com.fieldops.auth.infrastructure.config.JwtProperties;
import com.fieldops.auth.infrastructure.config.RsaKeyProvider;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtProperties properties;
    private RsaKeyProvider rsaKeyProvider;
    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() throws Exception {
        properties = new JwtProperties();
        properties.setKeyPath(tempDir.toString());
        properties.setKeyId("test-kid");
        properties.setAccessTokenExpirationSeconds(900);
        properties.setRefreshTokenExpirationDays(7);

        rsaKeyProvider = new RsaKeyProvider(properties);
        rsaKeyProvider.afterPropertiesSet();

        tokenService = new JwtTokenService(properties, rsaKeyProvider, refreshTokenRepository);
    }

    private User createSampleUser() {
        User user = new User();
        user.setId(10L);
        user.setUsername("supervisor");
        user.setFullName("Test Supervisor");
        user.setEmail("supervisor@fieldops.com");
        user.setActive(true);
        user.setRoles(Set.of(new Role("ROLE_SUPERVISOR")));
        return user;
    }

    @Test
    void generateAccessTokenContainsCorrectClaimsAndSignature() throws Exception {
        User user = createSampleUser();
        String tokenStr = tokenService.generateAccessToken(user);

        assertThat(tokenStr).isNotBlank();
        SignedJWT jwt = SignedJWT.parse(tokenStr);

        boolean validSig = jwt.verify(new RSASSAVerifier(rsaKeyProvider.getPublicKey()));
        assertThat(validSig).isTrue();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("test-kid");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("supervisor");
        assertThat(jwt.getJWTClaimsSet().getLongClaim("userId")).isEqualTo(10L);
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ROLE_SUPERVISOR");
    }

    @Test
    void createRefreshTokenSavesHashedToken() {
        User user = createSampleUser();
        String rawToken = tokenService.createRefreshToken(user);

        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getTokenHash()).isEqualTo(JwtTokenService.sha256Hex(rawToken));
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void verifyRefreshTokenReturnsTokenWhenValid() {
        String rawToken = "raw-refresh-token-12345";
        String hash = JwtTokenService.sha256Hex(rawToken);

        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash);
        token.setRevoked(false);
        token.setExpiresAt(LocalDateTime.now().plusDays(3));

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        RefreshToken result = tokenService.verifyAndGetRefreshToken(rawToken);
        assertThat(result).isEqualTo(token);
    }

    @Test
    void verifyRefreshTokenThrowsWhenRevoked() {
        String rawToken = "revoked-token";
        String hash = JwtTokenService.sha256Hex(rawToken);

        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash);
        token.setRevoked(true);
        token.setExpiresAt(LocalDateTime.now().plusDays(3));

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.verifyAndGetRefreshToken(rawToken))
                .isInstanceOf(InvalidTokenException.class);
    }
}