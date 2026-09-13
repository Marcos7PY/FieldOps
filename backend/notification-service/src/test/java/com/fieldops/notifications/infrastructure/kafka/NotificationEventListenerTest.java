package com.fieldops.notifications.infrastructure.kafka;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCancelledPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.notifications.application.service.NotificationProcessingService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationProcessingService notificationProcessingService;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationProcessingService, notificationLogRepository);
    }

    @Test
    void shouldProcessNewEventAndAcknowledge() {
        String eventId = UUID.randomUUID().toString();
        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId("42")
                .setTechnicianEmail("tecnico42@fieldops.com")
                .setTechnicianName("Carlos Gomez")
                .setScheduledAt(Instant.now())
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

        when(notificationProcessingService.isAlreadyProcessed(eventId, "notification-group")).thenReturn(false);

        listener.onMessage(record, acknowledgment);

        verify(notificationProcessingService).processAndRecord(eq(event), eq("notification-group"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldSkipAlreadyProcessedEventAndAcknowledge() {
        String eventId = UUID.randomUUID().toString();
        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId("42")
                .setTechnicianEmail("tecnico42@fieldops.com")
                .setTechnicianName("Carlos Gomez")
                .setScheduledAt(Instant.now())
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
                "fieldops.work-orders.events", 0, 1L, "101", event
        );

        when(notificationProcessingService.isAlreadyProcessed(eventId, "notification-group")).thenReturn(true);

        listener.onMessage(record, acknowledgment);

        verify(notificationProcessingService, never()).processAndRecord(any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeAndPropagateExceptionWhenProcessingFails() {
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

        when(notificationProcessingService.isAlreadyProcessed(eventId, "notification-group")).thenReturn(false);
        doThrow(new RuntimeException("Mail server down"))
                .when(notificationProcessingService).processAndRecord(any(), any());

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 3L, "104", event
        );

        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Mail server down");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shouldHandleDltAndLogFailure() {
        String eventId = UUID.randomUUID().toString();
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_ASSIGNED")
                .setOrderId(105L)
                .setOrderCode("ORD-2026-0005")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCancelledPayload("supervisor", "Test DLT"))
                .build();

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events-dlt", 0, 5L, "105", event
        );

        listener.handleDlt(record, "fieldops.work-orders.events-dlt", "Persistent failure after retries", acknowledgment);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getStatus()).isEqualTo("DLT_FAILED");
        assertThat(saved.getRecipient()).isEqualTo("DLT");

        verify(acknowledgment).acknowledge();
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

        when(notificationProcessingService.isAlreadyProcessed(any(), any())).thenReturn(false);

        listener.onMessage(record, acknowledgment);

        assertThat(MDC.get("traceId")).isNull();
        verify(acknowledgment).acknowledge();
    }
}
