package com.fieldops.orders.infrastructure.outbox;

import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.orders.domain.model.OutboxEvent;
import com.fieldops.orders.infrastructure.persistence.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private org.springframework.kafka.core.KafkaOperations<String, Object> kafkaOperations;

    private final WorkOrderEventSerializer serializer = new WorkOrderEventSerializer();
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxEventRepository, kafkaOperations, serializer);
    }

    private OutboxEvent createSampleOutboxEvent(Long id, Long aggregateId, String eventType) {
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setOrderId(aggregateId)
                .setOrderCode("ORD-" + aggregateId)
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload("Title " + aggregateId, 1L, "Client 1", "HIGH", "admin"))
                .build();

        String payloadJson = serializer.toJson(event);
        OutboxEvent outboxEvent = new OutboxEvent(event.getEventId(), aggregateId, eventType, payloadJson, LocalDateTime.now());
        outboxEvent.setId(id);
        return outboxEvent;
    }

    @Test
    void shouldPublishPendingEventsSuccessfully() {
        OutboxEvent e1 = createSampleOutboxEvent(1L, 101L, "ORDER_CREATED");
        OutboxEvent e2 = createSampleOutboxEvent(2L, 102L, "ORDER_ASSIGNED");

        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 100)))
                .thenReturn(List.of(e1, e2));
        when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        MDC.put("traceId", "trace-outbox-12345");
        try {
            publisher.publishPendingEvents();
        } finally {
            MDC.remove("traceId");
        }

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaOperations, times(2)).send(recordCaptor.capture());

        List<ProducerRecord<String, Object>> capturedRecords = recordCaptor.getAllValues();
        assertThat(capturedRecords.get(0).topic()).isEqualTo(OutboxPublisher.TOPIC);
        assertThat(capturedRecords.get(0).key()).isEqualTo("101");
        assertThat(capturedRecords.get(0).headers().lastHeader("X-Trace-Id")).isNotNull();
        assertThat(new String(capturedRecords.get(0).headers().lastHeader("X-Trace-Id").value()))
                .isEqualTo("trace-outbox-12345");

        assertThat(capturedRecords.get(1).key()).isEqualTo("102");

        assertThat(e1.getPublishedAt()).isNotNull();
        assertThat(e2.getPublishedAt()).isNotNull();
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void shouldHaltBatchWhenKafkaSendFailsToPreserveOrdering() {
        OutboxEvent e1 = createSampleOutboxEvent(1L, 201L, "ORDER_CREATED");
        OutboxEvent e2 = createSampleOutboxEvent(2L, 201L, "ORDER_ASSIGNED");

        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 100)))
                .thenReturn(List.of(e1, e2));
        when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenThrow(new RuntimeException("Kafka broker unavailable"));

        publisher.publishPendingEvents();

        // Only first event was attempted; second was halted to preserve ordering
        verify(kafkaOperations, times(1)).send(any(ProducerRecord.class));
        assertThat(e1.getPublishedAt()).isNull();
        assertThat(e2.getPublishedAt()).isNull();
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 100)))
                .thenReturn(List.of());

        publisher.publishPendingEvents();

        verify(kafkaOperations, never()).send(any(ProducerRecord.class));
    }

    @Test
    void shouldGenerateTraceIdWhenNotPresentInMDC() {
        OutboxEvent e1 = createSampleOutboxEvent(1L, 101L, "ORDER_CREATED");

        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 100)))
                .thenReturn(List.of(e1));
        when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaOperations).send(recordCaptor.capture());

        var header = recordCaptor.getValue().headers().lastHeader("X-Trace-Id");
        assertThat(header).isNotNull();
        assertThat(new String(header.value())).isNotBlank();
    }
}
