package com.fieldops.orders.domain.model;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCancelledPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.OrderStartedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderAvroEventTest {

    @Test
    void shouldSerializeAndDeserializeOrderCreatedEvent() throws IOException {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        OrderCreatedPayload createdPayload = OrderCreatedPayload.newBuilder()
                .setTitle("Reparacion de transformador")
                .setClientId(10L)
                .setClientName("Hospital Central")
                .setPriority("HIGH")
                .setCreatedBy("supervisor")
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CREATED")
                .setOrderId(101L)
                .setOrderCode("ORD-2026-0001")
                .setOccurredAt(now)
                .setSchemaVersion(1)
                .setPayload(createdPayload)
                .build();

        byte[] serialized = serialize(event);
        WorkOrderEvent deserialized = deserialize(serialized);

        assertThat(deserialized.getEventId()).isEqualTo(eventId);
        assertThat(deserialized.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(deserialized.getOrderId()).isEqualTo(101L);
        assertThat(deserialized.getOrderCode()).isEqualTo("ORD-2026-0001");
        assertThat(deserialized.getSchemaVersion()).isEqualTo(1);
        assertThat(deserialized.getPayload()).isInstanceOf(OrderCreatedPayload.class);

        OrderCreatedPayload payload = (OrderCreatedPayload) deserialized.getPayload();
        assertThat(payload.getTitle()).isEqualTo("Reparacion de transformador");
        assertThat(payload.getClientId()).isEqualTo(10L);
        assertThat(payload.getClientName()).isEqualTo("Hospital Central");
        assertThat(payload.getPriority()).isEqualTo("HIGH");
        assertThat(payload.getCreatedBy()).isEqualTo("supervisor");
    }

    @Test
    void shouldBuildAllFivePayloadTypes() {
        Instant now = Instant.now();

        OrderCreatedPayload p1 = OrderCreatedPayload.newBuilder()
                .setTitle("T1")
                .setClientId(1L)
                .setClientName("C1")
                .setPriority("MEDIUM")
                .setCreatedBy("user1")
                .build();

        OrderAssignedPayload p2 = OrderAssignedPayload.newBuilder()
                .setTechnicianId("tech-1")
                .setTechnicianEmail("tech1@fieldops.com")
                .setTechnicianName("Juan Perez")
                .setScheduledAt(now)
                .build();

        OrderStartedPayload p3 = OrderStartedPayload.newBuilder()
                .setTechnicianId("tech-1")
                .setStartedAt(now)
                .build();

        OrderCompletedPayload p4 = OrderCompletedPayload.newBuilder()
                .setTechnicianId("tech-1")
                .setCompletedAt(now)
                .setDurationMinutes(45)
                .setEvidenceCount(3)
                .build();

        OrderCancelledPayload p5 = OrderCancelledPayload.newBuilder()
                .setCancelledBy("sup1")
                .setReason("Cliente no disponible")
                .build();

        assertThat(p1.getTitle()).isEqualTo("T1");
        assertThat(p2.getTechnicianName()).isEqualTo("Juan Perez");
        assertThat(p3.getTechnicianId()).isEqualTo("tech-1");
        assertThat(p4.getDurationMinutes()).isEqualTo(45);
        assertThat(p5.getReason()).isEqualTo("Cliente no disponible");
    }

    private byte[] serialize(WorkOrderEvent event) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        SpecificDatumWriter<WorkOrderEvent> writer = new SpecificDatumWriter<>(WorkOrderEvent.class);
        writer.write(event, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    private WorkOrderEvent deserialize(byte[] data) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(in, null);
        SpecificDatumReader<WorkOrderEvent> reader = new SpecificDatumReader<>(WorkOrderEvent.class);
        return reader.read(null, decoder);
    }
}
