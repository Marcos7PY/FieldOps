package com.fieldops.orders.application.service;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCancelledPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.OrderStartedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.orders.domain.model.OutboxEvent;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.infrastructure.outbox.WorkOrderEventSerializer;
import com.fieldops.orders.infrastructure.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class TransactionalOutboxService implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final WorkOrderEventSerializer serializer;

    public TransactionalOutboxService(OutboxEventRepository outboxEventRepository, WorkOrderEventSerializer serializer) {
        this.outboxEventRepository = outboxEventRepository;
        this.serializer = serializer;
    }

    @Override
    @Transactional
    public void recordOrderCreated(WorkOrder order, Long createdBy) {
        OrderCreatedPayload payload = OrderCreatedPayload.newBuilder()
                .setTitle(order.getTitle())
                .setClientId(order.getClient().getId())
                .setClientName(order.getClient().getBusinessName())
                .setPriority(order.getPriority().name())
                .setCreatedBy(String.valueOf(createdBy))
                .build();

        WorkOrderEvent event = buildEvent("ORDER_CREATED", order, payload);
        saveOutboxEvent(event, order.getId());
    }

    @Override
    @Transactional
    public void recordOrderAssigned(WorkOrder order) {
        Instant scheduledInstant = order.getScheduledAt() != null
                ? order.getScheduledAt().toInstant(ZoneOffset.UTC)
                : Instant.now();

        String techIdStr = String.valueOf(order.getAssignedTechnicianId());
        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId(techIdStr)
                .setScheduledAt(scheduledInstant)
                .build();

        WorkOrderEvent event = buildEvent("ORDER_ASSIGNED", order, payload);
        saveOutboxEvent(event, order.getId());
    }

    @Override
    @Transactional
    public void recordOrderStarted(WorkOrder order) {
        Instant startedInstant = order.getStartedAt() != null
                ? order.getStartedAt().toInstant(ZoneOffset.UTC)
                : Instant.now();

        OrderStartedPayload payload = OrderStartedPayload.newBuilder()
                .setTechnicianId(String.valueOf(order.getAssignedTechnicianId()))
                .setStartedAt(startedInstant)
                .build();

        WorkOrderEvent event = buildEvent("ORDER_STARTED", order, payload);
        saveOutboxEvent(event, order.getId());
    }

    @Override
    @Transactional
    public void recordOrderCompleted(WorkOrder order, int evidenceCount) {
        Instant completedInstant = order.getCompletedAt() != null
                ? order.getCompletedAt().toInstant(ZoneOffset.UTC)
                : Instant.now();

        int durationMinutes = 0;
        if (order.getStartedAt() != null && order.getCompletedAt() != null) {
            durationMinutes = (int) Duration.between(order.getStartedAt(), order.getCompletedAt()).toMinutes();
            if (durationMinutes < 0) {
                durationMinutes = 0;
            }
        }

        OrderCompletedPayload payload = OrderCompletedPayload.newBuilder()
                .setTechnicianId(String.valueOf(order.getAssignedTechnicianId()))
                .setCompletedAt(completedInstant)
                .setDurationMinutes(durationMinutes)
                .setEvidenceCount(evidenceCount)
                .setCreatedBy(order.getCreatedBy() != null ? String.valueOf(order.getCreatedBy()) : null)
                .build();

        WorkOrderEvent event = buildEvent("ORDER_COMPLETED", order, payload);
        saveOutboxEvent(event, order.getId());
    }

    @Override
    @Transactional
    public void recordOrderCancelled(WorkOrder order, Long cancelledBy, String reason) {
        OrderCancelledPayload payload = OrderCancelledPayload.newBuilder()
                .setCancelledBy(String.valueOf(cancelledBy))
                .setReason(reason != null && !reason.isBlank() ? reason : "Order cancelled")
                .build();

        WorkOrderEvent event = buildEvent("ORDER_CANCELLED", order, payload);
        saveOutboxEvent(event, order.getId());
    }

    private WorkOrderEvent buildEvent(String eventType, WorkOrder order, Object payload) {
        return WorkOrderEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setOrderId(order.getId())
                .setOrderCode(order.getCode())
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();
    }

    private void saveOutboxEvent(WorkOrderEvent event, Long orderId) {
        String jsonPayload = serializer.toJson(event);
        OutboxEvent outboxEvent = new OutboxEvent(
                event.getEventId(),
                orderId,
                event.getEventType(),
                jsonPayload,
                LocalDateTime.now()
        );
        outboxEventRepository.save(outboxEvent);
    }
}
