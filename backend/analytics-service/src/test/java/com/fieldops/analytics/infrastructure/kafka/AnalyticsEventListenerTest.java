package com.fieldops.analytics.infrastructure.kafka;

import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.analytics.application.service.AnalyticsProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventListenerTest {

    @Mock
    private AnalyticsProjectionService projectionService;

    @Mock
    private Acknowledgment acknowledgment;

    private AnalyticsEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AnalyticsEventListener(projectionService);
    }

    @Test
    void shouldProjectNewEventAndAcknowledge() {
        String eventId = UUID.randomUUID().toString();
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CREATED")
                .setOrderId(100L)
                .setOrderCode("ORD-100")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload("Title", 1L, "Client", "LOW", "user"))
                .build();

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 0L, "100", event
        );

        when(projectionService.isAlreadyProcessed(eventId, "analytics-group")).thenReturn(false);

        listener.onMessage(record, acknowledgment);

        verify(projectionService).projectEvent(event, "analytics-group");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldSkipAlreadyProcessedEventAndAcknowledge() {
        String eventId = UUID.randomUUID().toString();
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CREATED")
                .setOrderId(100L)
                .setOrderCode("ORD-100")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload("Title", 1L, "Client", "LOW", "user"))
                .build();

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 1L, "100", event
        );

        when(projectionService.isAlreadyProcessed(eventId, "analytics-group")).thenReturn(true);

        listener.onMessage(record, acknowledgment);

        verify(projectionService, never()).projectEvent(any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldExtractTraceIdFromHeader() {
        String eventId = UUID.randomUUID().toString();
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CREATED")
                .setOrderId(100L)
                .setOrderCode("ORD-100")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload("Title", 1L, "Client", "LOW", "user"))
                .build();

        RecordHeaders headers = new RecordHeaders();
        headers.add("X-Trace-Id", "trace-analytics-99".getBytes(StandardCharsets.UTF_8));

        ConsumerRecord<String, WorkOrderEvent> record = new ConsumerRecord<>(
                "fieldops.work-orders.events", 0, 2L, 0L,
                org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE,
                0, 0, "100", event, headers, java.util.Optional.empty()
        );

        when(projectionService.isAlreadyProcessed(any(), any())).thenReturn(false);

        listener.onMessage(record, acknowledgment);

        assertThat(MDC.get("traceId")).isNull();
        verify(acknowledgment).acknowledge();
    }
}
