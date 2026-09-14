package com.fieldops.auth.application.service;

import com.fieldops.auth.application.dto.AuthResponse;
import com.fieldops.auth.application.dto.LoginRequest;
import com.fieldops.auth.application.dto.UserResponse;
import com.fieldops.auth.domain.exception.InvalidCredentialsException;
import com.fieldops.auth.domain.model.Role;
import com.fieldops.auth.domain.model.User;
import com.fieldops.auth.infrastructure.config.JwtProperties;
import com.fieldops.auth.infrastructure.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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

    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO.8/k0a2lXQdCqB0j8R1N1YFq9Jq6KGa";

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String clientIp = getClientIp();
        User user = userRepository.findByUsername(request.username())
                .orElse(null);

        if (user == null) {
            log.warn("Login failed: user '{}' not found from IP '{}'", request.username(), clientIp);
            // Mitigate timing-based user enumeration attacks:
            // Run BCrypt against a dummy hash so response time is consistent
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new InvalidCredentialsException();
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            log.warn("Login failed: account is locked until {} for user '{}' from IP '{}'",
                    user.getLockedUntil(), request.username(), clientIp);
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            log.warn("Login failed: inactive user '{}' from IP '{}'", request.username(), clientIp);
            passwordEncoder.matches(request.password(), user.getPasswordHash());
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                user.setLockedUntil(now.plusMinutes(15));
                log.warn("Account locked for 15 minutes due to {} failed attempts: user '{}' from IP '{}'",
                        attempts, request.username(), clientIp);
            } else {
                log.warn("Login failed (attempt {}/5) for user '{}' from IP '{}'",
                        attempts, request.username(), clientIp);
            }
            userRepository.save(user);
            throw new InvalidCredentialsException();
        }

        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
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

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String xForwardedFor = req.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
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

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new com.fieldops.auth.domain.exception.UserNotFoundException("User not found: " + id));
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
                user.getEmail(),
                roleNames
        );
    }
}
