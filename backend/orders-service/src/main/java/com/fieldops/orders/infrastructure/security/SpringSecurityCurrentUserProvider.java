package com.fieldops.orders.infrastructure.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<Long> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Object claim = jwtAuth.getToken().getClaim("userId");
            if (claim instanceof Number n) {
                return Optional.of(n.longValue());
            }
            if (claim instanceof String s) {
                try {
                    return Optional.of(Long.parseLong(s));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
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
        return hasAuthority("ROLE_SUPERVISOR");
    }

    @Override
    public boolean isTechnician() {
        return hasAuthority("ROLE_TECHNICIAN");
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
