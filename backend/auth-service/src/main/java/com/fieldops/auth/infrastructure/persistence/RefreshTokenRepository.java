package com.fieldops.auth.infrastructure.persistence;

import com.fieldops.auth.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM RefreshToken r WHERE r.revoked = true OR r.expiresAt < :now")
    int deleteExpiredOrRevokedTokens(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}
