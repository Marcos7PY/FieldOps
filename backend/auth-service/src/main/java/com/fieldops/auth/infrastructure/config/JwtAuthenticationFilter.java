package com.fieldops.auth.infrastructure.config;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RsaKeyProvider rsaKeyProvider;

    public JwtAuthenticationFilter(RsaKeyProvider rsaKeyProvider) {
        this.rsaKeyProvider = rsaKeyProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                SignedJWT signedJWT = SignedJWT.parse(token);
                RSASSAVerifier verifier = new RSASSAVerifier(rsaKeyProvider.getPublicKey());

                if (signedJWT.verify(verifier)) {
                    Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
                    if (expiration != null && expiration.after(new Date())) {
                        String username = signedJWT.getJWTClaimsSet().getSubject();
                        Long userId = signedJWT.getJWTClaimsSet().getLongClaim("userId");
                        List<String> roles = signedJWT.getJWTClaimsSet().getStringListClaim("roles");
                        if (roles == null) {
                            roles = Collections.emptyList();
                        }

                        List<SimpleGrantedAuthority> authorities = roles.stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();

                        UserPrincipal principal = new UserPrincipal(userId, username, roles);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        filterChain.doFilter(request, response);
    }
}