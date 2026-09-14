package com.fieldops.orders.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityCurrentUserProviderTest {

    private SpringSecurityCurrentUserProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SpringSecurityCurrentUserProvider();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Devuelve userId cuando el JWT contiene el claim userId numérico")
    void getCurrentUserId_whenJwtHasUserId_returnsId() {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "user1", "userId", 7L)
        );
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> userId = provider.getCurrentUserId();

        assertThat(userId).contains(7L);
    }

    @Test
    @DisplayName("Devuelve Optional.empty() cuando no hay autenticación en el SecurityContext")
    void getCurrentUserId_whenNoAuth_returnsEmpty() {
        Optional<Long> userId = provider.getCurrentUserId();

        assertThat(userId).isEmpty();
    }

    @Test
    @DisplayName("isSupervisor devuelve false si el token no tiene ROLE_SUPERVISOR")
    void isSupervisor_whenNotSupervisor_returnsFalse() {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "tecnico1", "userId", 2L)
        );
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(provider.isSupervisor()).isFalse();
        assertThat(provider.isTechnician()).isTrue();
    }
}
