package com.fieldops.analytics.application.service;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCancelledPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.OrderStartedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.analytics.domain.model.ProcessedEvent;
import com.fieldops.analytics.domain.model.ProcessedEventId;
import com.fieldops.analytics.domain.model.ProjectionCheckpoint;
import com.fieldops.analytics.infrastructure.persistence.ProcessedEventRepository;
import com.fieldops.analytics.infrastructure.persistence.ProjectionCheckpointRepository;
import com.fieldops.analytics.infrastructure.persistence.WorkOrderDailyMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAnalyticsProjectionServiceTest {

    @Mock
    private WorkOrderDailyMetricRepository metricRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ProjectionCheckpointRepository checkpointRepository;

    private DefaultAnalyticsProjectionService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAnalyticsProjectionService(
                metricRepository,
                processedEventRepository,
                checkpointRepository
        );
    }

    @Test
    void shouldCheckIfEventAlreadyProcessed() {
        String eventId = UUID.randomUUID().toString();
        ProcessedEventId id = new ProcessedEventId(eventId, "analytics-group");
        when(processedEventRepository.existsById(id)).thenReturn(true);

        assertThat(service.isAlreadyProcessed(eventId, "analytics-group")).isTrue();
    }

    @Test
    void shouldProjectOrderCreatedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-09-13T10:00:00Z");

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CREATED")
                .setOrderId(100L)
                .setOrderCode("ORD-100")
                .setOccurredAt(now)
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload("T", 1L, "C", "HIGH", "u"))
                .build();

        when(checkpointRepository.findById("analytics-group")).thenReturn(Optional.empty());

        service.projectEvent(event, "analytics-group");

        verify(metricRepository).upsertMetric(
                eq(LocalDate.of(2026, 9, 13)),
                eq(0L),
                eq("DRAFT"),
                eq(1),
                eq(null),
                any(LocalDateTime.class)
        );

        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getId().getEventId()).isEqualTo(eventId);

        ArgumentCaptor<ProjectionCheckpoint> checkpointCaptor = ArgumentCaptor.forClass(ProjectionCheckpoint.class);
        verify(checkpointRepository).save(checkpointCaptor.capture());
        assertThat(checkpointCaptor.getValue().getEventsProcessed()).isEqualTo(1L);
    }

    @Test
    void shouldProjectOrderAssignedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-09-13T11:00:00Z");

        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId("42")
                .setTechnicianEmail("tech42@fieldops.com")
                .setTechnicianName("Carlos")
                .setScheduledAt(now)
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_ASSIGNED")
                .setOrderId(101L)
                .setOrderCode("ORD-101")
                .setOccurredAt(now)
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        service.projectEvent(event, "analytics-group");

        verify(metricRepository).upsertMetric(
                eq(LocalDate.of(2026, 9, 13)),
                eq(42L),
                eq("ASSIGNED"),
                eq(1),
                eq(null),
                any(LocalDateTime.class)
        );
    }

    @Test
    void shouldProjectOrderStartedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-09-13T12:00:00Z");

        OrderStartedPayload payload = OrderStartedPayload.newBuilder()
                .setTechnicianId("42")
                .setStartedAt(now)
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_STARTED")
                .setOrderId(102L)
                .setOrderCode("ORD-102")
                .setOccurredAt(now)
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        service.projectEvent(event, "analytics-group");

        verify(metricRepository).upsertMetric(
                eq(LocalDate.of(2026, 9, 13)),
                eq(42L),
                eq("IN_PROGRESS"),
                eq(1),
                eq(null),
                any(LocalDateTime.class)
        );
    }

    @Test
    void shouldProjectOrderCompletedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-09-13T13:00:00Z");

        OrderCompletedPayload payload = OrderCompletedPayload.newBuilder()
                .setTechnicianId("42")
                .setCompletedAt(now)
                .setDurationMinutes(75)
                .setEvidenceCount(2)
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_COMPLETED")
                .setOrderId(103L)
                .setOrderCode("ORD-103")
                .setOccurredAt(now)
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        service.projectEvent(event, "analytics-group");

        verify(metricRepository).upsertMetric(
                eq(LocalDate.of(2026, 9, 13)),
                eq(42L),
                eq("COMPLETED"),
                eq(1),
                eq(BigDecimal.valueOf(75)),
                any(LocalDateTime.class)
        );
    }

    @Test
    void shouldProjectOrderCancelledEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-09-13T14:00:00Z");

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CANCELLED")
                .setOrderId(104L)
                .setOrderCode("ORD-104")
                .setOccurredAt(now)
                .setSchemaVersion(1)
                .setPayload(new OrderCancelledPayload("sup", "Customer request"))
                .build();

        service.projectEvent(event, "analytics-group");

        verify(metricRepository).upsertMetric(
                eq(LocalDate.of(2026, 9, 13)),
                eq(0L),
                eq("CANCELLED"),
                eq(1),
                eq(null),
                any(LocalDateTime.class)
        );
    }
}
