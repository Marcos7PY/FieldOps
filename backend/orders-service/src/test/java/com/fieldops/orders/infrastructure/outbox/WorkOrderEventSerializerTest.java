package com.fieldops.orders.infrastructure.outbox;

import com.fieldops.events.avro.OrderCancelledPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkOrderEventSerializerTest {

    private final WorkOrderEventSerializer serializer = new WorkOrderEventSerializer();

    @Test
    void shouldSerializeAndDeserializeJson() {
        String eventId = UUID.randomUUID().toString();
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CANCELLED")
                .setOrderId(99L)
                .setOrderCode("ORD-999")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCancelledPayload("user1", "Client request"))
                .build();

        String json = serializer.toJson(event);
        assertThat(json).contains(eventId);
        assertThat(json).contains("ORDER_CANCELLED");

        WorkOrderEvent parsed = serializer.fromJson(json);
        assertThat(parsed.getEventId()).isEqualTo(eventId);
        assertThat(parsed.getOrderId()).isEqualTo(99L);
        assertThat(parsed.getPayload()).isInstanceOf(OrderCancelledPayload.class);
    }

    @Test
    void shouldThrowOnInvalidJson() {
        assertThatThrownBy(() -> serializer.fromJson("invalid json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize WorkOrderEvent");
    }
}
