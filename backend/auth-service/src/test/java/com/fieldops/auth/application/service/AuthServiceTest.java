package com.fieldops.auth.application.service;

import com.fieldops.auth.application.dto.AuthResponse;
import com.fieldops.auth.application.dto.LoginRequest;
import com.fieldops.auth.domain.exception.InvalidCredentialsException;
import com.fieldops.auth.domain.model.Role;
import com.fieldops.auth.domain.model.User;
import com.fieldops.auth.infrastructure.config.JwtProperties;
import com.fieldops.auth.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setAccessTokenExpirationSeconds(900);
        authService = new AuthService(userRepository, passwordEncoder, tokenService, jwtProperties);
    }

    @Test
    void loginWithValidCredentialsReturnsAuthResponse() {
        User user = new User();
        user.setId(1L);
        user.setUsername("supervisor");
        user.setFullName("Head Supervisor");
        user.setPasswordHash("hashed_password");
        user.setActive(true);
        user.setRoles(Set.of(new Role("ROLE_SUPERVISOR")));

        when(userRepository.findByUsername("supervisor")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Demo2026!", "hashed_password")).thenReturn(true);
        when(tokenService.generateAccessToken(user)).thenReturn("mock.jwt.token");
        when(tokenService.createRefreshToken(user)).thenReturn("mock-refresh-token");

        LoginRequest req = new LoginRequest("supervisor", "Demo2026!");
        AuthResponse response = authService.login(req);

        assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.user().username()).isEqualTo("supervisor");
        assertThat(response.user().roles()).containsExactly("ROLE_SUPERVISOR");
    }

    @Test
    void loginWithWrongPasswordThrowsInvalidCredentials() {
        User user = new User();
        user.setUsername("supervisor");
        user.setPasswordHash("hashed_password");
        user.setActive(true);

        when(userRepository.findByUsername("supervisor")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed_password")).thenReturn(false);

        LoginRequest req = new LoginRequest("supervisor", "WrongPassword");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithInactiveUserThrowsInvalidCredentials() {
        User user = new User();
        user.setUsername("disabled");
        user.setActive(false);

        when(userRepository.findByUsername("disabled")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest("disabled", "Demo2026!");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithNonexistentUserThrowsInvalidCredentials() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest("unknown", "Demo2026!");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}