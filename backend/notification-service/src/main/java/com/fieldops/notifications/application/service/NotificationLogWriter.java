package com.fieldops.notifications.application.service;

import com.fieldops.notifications.domain.model.NotificationLog;
import com.fieldops.notifications.domain.model.ProcessedEvent;
import com.fieldops.notifications.infrastructure.persistence.NotificationLogRepository;
import com.fieldops.notifications.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class NotificationLogWriter {

    private final NotificationLogRepository notificationLogRepository;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationLogWriter(
            NotificationLogRepository notificationLogRepository,
            ProcessedEventRepository processedEventRepository
    ) {
        this.notificationLogRepository = notificationLogRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long recordAttempt(NotificationPlan plan, String eventId) {
        NotificationLog notificationLog = new NotificationLog(
                eventId,
                plan.recipient(),
                plan.subject(),
                LocalDateTime.now(),
                "PENDING"
        );
        notificationLog.setAttempts(1);
        NotificationLog saved = notificationLogRepository.save(notificationLog);
        return saved.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long logId) {
        notificationLogRepository.findById(logId).ifPresent(log -> {
            log.setStatus("SENT");
            log.setSentAt(LocalDateTime.now());
            notificationLogRepository.save(log);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long logId, String errorMessage) {
        notificationLogRepository.findById(logId).ifPresent(log -> {
            log.setStatus("FAILED");
            if (errorMessage != null && errorMessage.length() > 1000) {
                log.setErrorMessage(errorMessage.substring(0, 1000));
            } else {
                log.setErrorMessage(errorMessage);
            }
            notificationLogRepository.save(log);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventId, String consumerGroup) {
        processedEventRepository.save(new ProcessedEvent(eventId, consumerGroup, LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSkipped(String eventId, String reason) {
        NotificationLog notificationLog = new NotificationLog(
                eventId,
                "UNRESOLVED",
                reason,
                LocalDateTime.now(),
                "SKIPPED_NO_RECIPIENT"
        );
        notificationLog.setErrorMessage(reason);
        notificationLogRepository.save(notificationLog);
    }
}
