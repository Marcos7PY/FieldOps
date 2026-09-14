package com.fieldops.orders.application.service;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCancelledPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.OrderStartedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.orders.domain.model.Client;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.OutboxEvent;
import com.fieldops.orders.domain.model.Priority;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.infrastructure.outbox.WorkOrderEventSerializer;
import com.fieldops.orders.infrastructure.persistence.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionalOutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final WorkOrderEventSerializer serializer = new WorkOrderEventSerializer();
    private TransactionalOutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new TransactionalOutboxService(outboxEventRepository, serializer);
    }

    private WorkOrder createOrder() {
        Client client = new Client();
        client.setId(10L);
        client.setBusinessName("Global Power Corp");

        WorkOrder order = new WorkOrder();
        order.setId(100L);
        order.setCode("ORD-2026-0001");
        order.setTitle("Revision de transformador");
        order.setPriority(Priority.HIGH);
        order.setStatus(OrderStatus.DRAFT);
        order.setClient(client);
        order.setCreatedBy(1L);
        order.setCreatedAt(LocalDateTime.now().minusHours(2));
        order.setScheduledAt(LocalDateTime.now().plusDays(1));
        order.setStartedAt(LocalDateTime.now().minusHours(1));
        order.setCompletedAt(LocalDateTime.now());
        order.setAssignedTechnicianId(42L);
        return order;
    }

    @Test
    void shouldRecordOrderCreatedEvent() {
        WorkOrder order = createOrder();
        outboxService.recordOrderCreated(order, 1L);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo(100L);
        assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getPublishedAt()).isNull();

        WorkOrderEvent deserialized = serializer.fromJson(saved.getPayload());
        assertThat(deserialized.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(deserialized.getOrderId()).isEqualTo(100L);
        assertThat(deserialized.getPayload()).isInstanceOf(OrderCreatedPayload.class);
        OrderCreatedPayload payload = (OrderCreatedPayload) deserialized.getPayload();
        assertThat(payload.getTitle()).isEqualTo("Revision de transformador");
        assertThat(payload.getClientId()).isEqualTo(10L);
    }

    @Test
    void shouldRecordOrderAssignedEvent() {
        WorkOrder order = createOrder();
        outboxService.recordOrderAssigned(order);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("ORDER_ASSIGNED");

        WorkOrderEvent deserialized = serializer.fromJson(saved.getPayload());
        assertThat(deserialized.getPayload()).isInstanceOf(OrderAssignedPayload.class);
        OrderAssignedPayload payload = (OrderAssignedPayload) deserialized.getPayload();
        assertThat(payload.getTechnicianId()).isEqualTo("42");
        assertThat(payload.getTechnicianEmail()).isNull();
    }

    @Test
    void shouldRecordOrderStartedEvent() {
        WorkOrder order = createOrder();
        outboxService.recordOrderStarted(order);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("ORDER_STARTED");

        WorkOrderEvent deserialized = serializer.fromJson(saved.getPayload());
        assertThat(deserialized.getPayload()).isInstanceOf(OrderStartedPayload.class);
        OrderStartedPayload payload = (OrderStartedPayload) deserialized.getPayload();
        assertThat(payload.getTechnicianId()).isEqualTo("42");
    }

    @Test
    void shouldRecordOrderCompletedEvent() {
        WorkOrder order = createOrder();
        outboxService.recordOrderCompleted(order, 2);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("ORDER_COMPLETED");

        WorkOrderEvent deserialized = serializer.fromJson(saved.getPayload());
        assertThat(deserialized.getPayload()).isInstanceOf(OrderCompletedPayload.class);
        OrderCompletedPayload payload = (OrderCompletedPayload) deserialized.getPayload();
        assertThat(payload.getTechnicianId()).isEqualTo("42");
        assertThat(payload.getDurationMinutes()).isEqualTo(60);
        assertThat(payload.getEvidenceCount()).isEqualTo(2);
        assertThat(payload.getCreatedBy()).isEqualTo("1");
    }

    @Test
    void shouldRecordOrderCancelledEvent() {
        WorkOrder order = createOrder();
        outboxService.recordOrderCancelled(order, 1L, "Cancelled by supervisor");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("ORDER_CANCELLED");

        WorkOrderEvent deserialized = serializer.fromJson(saved.getPayload());
        assertThat(deserialized.getPayload()).isInstanceOf(OrderCancelledPayload.class);
        OrderCancelledPayload payload = (OrderCancelledPayload) deserialized.getPayload();
        assertThat(payload.getCancelledBy()).isEqualTo("1");
        assertThat(payload.getReason()).isEqualTo("Cancelled by supervisor");
    }
}
