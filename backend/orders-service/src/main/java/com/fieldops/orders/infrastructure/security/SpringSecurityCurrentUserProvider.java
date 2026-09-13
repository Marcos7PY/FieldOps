package com.fieldops.orders.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    private final HttpServletRequest request;

    public SpringSecurityCurrentUserProvider(@Autowired(required = false) HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public Optional<Long> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            Object userIdClaim = jwt.getClaim("userId");
            if (userIdClaim instanceof Number number) {
                return Optional.of(number.longValue());
            } else if (userIdClaim instanceof String str) {
                try {
                    return Optional.of(Long.parseLong(str));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (request != null) {
            String headerVal = request.getHeader("X-User-Id");
            if (headerVal != null && !headerVal.isBlank()) {
                try {
                    return Optional.of(Long.parseLong(headerVal.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !(auth instanceof AnonymousAuthenticationToken)) {
            return Optional.ofNullable(auth.getName());
        }
        return Optional.empty();
    }

    @Override
    public boolean isSupervisor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_SUPERVISOR"));
        }
        if (request != null) {
            String roleHeader = request.getHeader("X-User-Role");
            return "ROLE_SUPERVISOR".equalsIgnoreCase(roleHeader);
        }
        return false;
    }

    @Override
    public boolean isTechnician() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_TECHNICIAN"));
        }
        if (request != null) {
            String roleHeader = request.getHeader("X-User-Role");
            return "ROLE_TECHNICIAN".equalsIgnoreCase(roleHeader);
        }
        return false;
    }
}