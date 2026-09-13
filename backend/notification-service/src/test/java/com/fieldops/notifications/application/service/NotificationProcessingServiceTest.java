package com.fieldops.notifications.application.service;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.notifications.domain.model.NotificationLog;
import com.fieldops.notifications.domain.model.ProcessedEvent;
import com.fieldops.notifications.domain.model.ProcessedEventId;
import com.fieldops.notifications.infrastructure.persistence.NotificationLogRepository;
import com.fieldops.notifications.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationProcessingServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private EmailService emailService;

    private NotificationProcessingService service;

    @BeforeEach
    void setUp() {
        service = new DefaultNotificationProcessingService(
                processedEventRepository,
                notificationLogRepository,
                emailService
        );
    }

    @Test
    void shouldReturnTrueWhenEventIsAlreadyProcessed() {
        String eventId = UUID.randomUUID().toString();
        ProcessedEventId id = new ProcessedEventId(eventId, "notification-group");
        when(processedEventRepository.existsById(id)).thenReturn(true);

        assertThat(service.isAlreadyProcessed(eventId, "notification-group")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenEventIsNotProcessed() {
        String eventId = UUID.randomUUID().toString();
        ProcessedEventId id = new ProcessedEventId(eventId, "notification-group");
        when(processedEventRepository.existsById(id)).thenReturn(false);

        assertThat(service.isAlreadyProcessed(eventId, "notification-group")).isFalse();
    }

    @Test
    void shouldProcessOrderAssignedAndSaveProcessedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant scheduledAt = Instant.now();

        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId("42")
                .setTechnicianEmail("tecnico42@fieldops.com")
                .setTechnicianName("Carlos Gomez")
                .setScheduledAt(scheduledAt)
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_ASSIGNED")
                .setOrderId(101L)
                .setOrderCode("ORD-2026-0001")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        service.processAndRecord(event, "notification-group");

        verify(emailService).sendOrderAssignedNotification(
                eq("tecnico42@fieldops.com"),
                eq("Carlos Gomez"),
                eq("ORD-2026-0001"),
                any(String.class)
        );

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SENT");

        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getId().getEventId()).isEqualTo(eventId);
        assertThat(processedCaptor.getValue().getId().getConsumerGroup()).isEqualTo("notification-group");
    }

    @Test
    void shouldProcessOrderCompletedAndSaveProcessedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant completedAt = Instant.now();

        OrderCompletedPayload payload = OrderCompletedPayload.newBuilder()
                .setTechnicianId("42")
                .setCompletedAt(completedAt)
                .setDurationMinutes(90)
                .setEvidenceCount(2)
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_COMPLETED")
                .setOrderId(102L)
                .setOrderCode("ORD-2026-0002")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        service.processAndRecord(event, "notification-group");

        verify(emailService).sendOrderCompletedNotification(
                eq("supervisor@fieldops.com"),
                eq("ORD-2026-0002"),
                eq("42"),
                eq(90),
                eq(2),
                any(String.class)
        );

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SENT");

        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getId().getEventId()).isEqualTo(eventId);
    }

    @Test
    void shouldRecordProcessedEventForIgnoredTypes() {
        String eventId = UUID.randomUUID().toString();
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CREATED")
                .setOrderId(103L)
                .setOrderCode("ORD-2026-0003")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload("T", 1L, "C", "LOW", "u"))
                .build();

        service.processAndRecord(event, "notification-group");

        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getId().getEventId()).isEqualTo(eventId);
    }
}
