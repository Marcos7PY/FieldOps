package com.fieldops.notifications.application.service;

import com.fieldops.notifications.domain.model.NotificationLog;
import com.fieldops.notifications.infrastructure.persistence.NotificationLogRepository;
import com.fieldops.notifications.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationLogWriterTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private NotificationLogWriter writer;

    @BeforeEach
    void setUp() {
        writer = new NotificationLogWriter(notificationLogRepository, processedEventRepository);
    }

    @Test
    void shouldRecordAttemptSuccessfully() {
        NotificationPlan plan = new OrderAssignedPlan("tech@fieldops.com", "Tech Name", "OT-1", "2026-09-13T10:00:00");

        NotificationLog savedLog = new NotificationLog("evt-1", "tech@fieldops.com", "Nueva orden asignada: OT-1", LocalDateTime.now(), "PENDING");
        savedLog.setId(42L);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenReturn(savedLog);

        Long logId = writer.recordAttempt(plan, "evt-1");

        assertThat(logId).isEqualTo(42L);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-1");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldMarkSentSuccessfully() {
        NotificationLog log = new NotificationLog("evt-2", "tech@fieldops.com", "Orden", LocalDateTime.now(), "PENDING");
        log.setId(10L);
        when(notificationLogRepository.findById(10L)).thenReturn(Optional.of(log));

        writer.markSent(10L);

        assertThat(log.getStatus()).isEqualTo("SENT");
        assertThat(log.getSentAt()).isNotNull();
        verify(notificationLogRepository).save(log);
    }

    @Test
    void shouldMarkFailedWithTruncatedErrorMessage() {
        NotificationLog log = new NotificationLog("evt-3", "tech@fieldops.com", "Orden", LocalDateTime.now(), "PENDING");
        log.setId(20L);
        when(notificationLogRepository.findById(20L)).thenReturn(Optional.of(log));

        String longError = "E".repeat(1500);
        writer.markFailed(20L, longError);

        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getErrorMessage()).hasSize(1000);
        verify(notificationLogRepository).save(log);
    }
}
