package com.fieldops.notifications.infrastructure.kafka;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCancelledPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.notifications.application.service.EmailService;
import com.fieldops.notifications.domain.model.NotificationLog;
import com.fieldops.notifications.infrastructure.persistence.NotificationLogRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(emailService, notificationLogRepository);
    }

    @Test
    void shouldSendEmailAndLogOnOrderAssignedEvent() {
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

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 0L, "101", event
        );

        listener.onMessage(record, acknowledgment);

        verify(emailService).sendOrderAssignedNotification(
                eq("tecnico42@fieldops.com"),
                eq("Carlos Gomez"),
                eq("ORD-2026-0001"),
                any(String.class)
        );

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getRecipient()).isEqualTo("tecnico42@fieldops.com");
        assertThat(saved.getStatus()).isEqualTo("SENT");

        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldSendEmailAndLogOnOrderCompletedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant completedAt = Instant.now();

        OrderCompletedPayload payload = OrderCompletedPayload.newBuilder()
                .setTechnicianId("42")
                .setCompletedAt(completedAt)
                .setDurationMinutes(85)
                .setEvidenceCount(3)
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

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 1L, "102", event
        );

        listener.onMessage(record, acknowledgment);

        verify(emailService).sendOrderCompletedNotification(
                eq("supervisor@fieldops.com"),
                eq("ORD-2026-0002"),
                eq("42"),
                eq(85),
                eq(3),
                any(String.class)
        );

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getRecipient()).isEqualTo("supervisor@fieldops.com");
        assertThat(saved.getStatus()).isEqualTo("SENT");

        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldIgnoreUnrelatedEventsWithoutSendingEmail() {
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType("ORDER_CREATED")
                .setOrderId(103L)
                .setOrderCode("ORD-2026-0003")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload("T", 1L, "C", "LOW", "user"))
                .build();

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 2L, "103", event
        );

        listener.onMessage(record, acknowledgment);

        verify(emailService, never()).sendOrderAssignedNotification(any(), any(), any(), any());
        verify(emailService, never()).sendOrderCompletedNotification(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(notificationLogRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldRecordFailedStatusWhenEmailFails() {
        String eventId = UUID.randomUUID().toString();
        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId("42")
                .setTechnicianEmail("fail@fieldops.com")
                .setTechnicianName("Carlos Gomez")
                .setScheduledAt(Instant.now())
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_ASSIGNED")
                .setOrderId(104L)
                .setOrderCode("ORD-2026-0004")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        doThrow(new RuntimeException("Mail server down"))
                .when(emailService).sendOrderAssignedNotification(any(), any(), any(), any());

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 3L, "104", event
        );

        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Mail server down");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shouldExtractTraceIdFromHeaders() {
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType("ORDER_CANCELLED")
                .setOrderId(105L)
                .setOrderCode("ORD-2026-0005")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCancelledPayload("sup", "Cancel"))
                .build();

        RecordHeaders headers = new RecordHeaders();
        headers.add("X-Trace-Id", "trace-notif-778899".getBytes(StandardCharsets.UTF_8));

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 4L, 0L,
                org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                0, 0, "105", event, headers, java.util.Optional.empty()
        );

        listener.onMessage(record, acknowledgment);

        // After processing, MDC is cleaned up
        assertThat(MDC.get("traceId")).isNull();
        verify(acknowledgment).acknowledge();
    }
}
