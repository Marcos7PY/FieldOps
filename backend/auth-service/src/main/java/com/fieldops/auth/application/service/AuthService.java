package com.fieldops.auth.application.service;

import com.fieldops.auth.application.dto.AuthResponse;
import com.fieldops.auth.application.dto.LoginRequest;
import com.fieldops.auth.application.dto.UserResponse;
import com.fieldops.auth.domain.exception.InvalidCredentialsException;
import com.fieldops.auth.domain.model.Role;
import com.fieldops.auth.domain.model.User;
import com.fieldops.auth.infrastructure.config.JwtProperties;
import com.fieldops.auth.infrastructure.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpirationSeconds(),
                toUserResponse(user)
        );
    }

    @Transactional
    public AuthResponse refresh(com.fieldops.auth.application.dto.RefreshTokenRequest request) {
        com.fieldops.auth.domain.model.RefreshToken oldToken = tokenService.verifyAndGetRefreshToken(request.refreshToken());
        User user = oldToken.getUser();

        if (!user.isActive()) {
            throw new com.fieldops.auth.domain.exception.InvalidTokenException("User is inactive");
        }

        oldToken.setRevoked(true);

        String newAccessToken = tokenService.generateAccessToken(user);
        String newRefreshToken = tokenService.createRefreshToken(user);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                jwtProperties.getAccessTokenExpirationSeconds(),
                toUserResponse(user)
        );
    }

    @Transactional
    public void logout(com.fieldops.auth.application.dto.LogoutRequest request) {
        if (request != null && request.refreshToken() != null) {
            tokenService.revokeRefreshToken(request.refreshToken());
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.fieldops.auth.domain.exception.UserNotFoundException("User not found: " + username));
        return toUserResponse(user);
    }

    public UserResponse toUserResponse(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toList();

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                roleNames
        );
    }
}