package com.fieldops.auth.infrastructure.jobs;

import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "fieldops.auth.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "${fieldops.auth.cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = refreshTokenRepository.deleteExpiredOrRevokedTokens(now);
        if (deleted > 0) {
            log.info("Cleaned up {} expired or revoked refresh tokens", deleted);
        }
    }
}
