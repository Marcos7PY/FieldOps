package com.fieldops.orders.infrastructure.outbox;

import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.orders.AbstractIntegrationTest;
import com.fieldops.orders.domain.model.OutboxEvent;
import com.fieldops.orders.infrastructure.persistence.OutboxEventRepository;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "fieldops.outbox.publisher.enabled=true",
        "fieldops.outbox.publisher.fixed-delay=3600000"
})
class OutboxPublisherIntegrationTest extends AbstractIntegrationTest {

    protected static final KafkaContainer KAFKA_CONTAINER;

    static {
        KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
        KAFKA_CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
        registry.add("spring.kafka.properties.schema.registry.url", () -> "mock://test-schema-registry");
    }

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private WorkOrderEventSerializer serializer;

    @org.springframework.boot.test.mock.mockito.MockBean
    private net.javacrumbs.shedlock.core.LockProvider lockProvider;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        org.mockito.Mockito.when(lockProvider.lock(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(() -> {}));
    }

    private OutboxEvent createValidOutboxEvent(Long aggregateId, String title, LocalDateTime createdAt) {
        String eventId = UUID.randomUUID().toString();
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CREATED")
                .setOrderId(aggregateId)
                .setOrderCode("ORD-" + aggregateId)
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload(title, 1L, "Client 1", "HIGH", "supervisor"))
                .build();

        return new OutboxEvent(eventId, aggregateId, "ORDER_CREATED", serializer.toJson(event), createdAt);
    }

    @Test
    @DisplayName("Caso 1: Dos eventos de la misma orden llegan a Kafka en el orden (created_at, id)")
    void shouldPublishEventsInOrderForSameAggregate() {
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1);
        Long orderId = 1001L;

        OutboxEvent event1 = outboxEventRepository.save(createValidOutboxEvent(orderId, "First Event", baseTime));
        OutboxEvent event2 = outboxEventRepository.save(createValidOutboxEvent(orderId, "Second Event", baseTime.plusMinutes(5)));

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put("schema.registry.url", "mock://test-schema-registry");
        props.put("specific.avro.reader", "true");

        try (KafkaConsumer<String, WorkOrderEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(OutboxEventDispatcher.TOPIC));
            consumer.poll(Duration.ofMillis(200));

            outboxPublisher.publishPendingEvents();

            List<WorkOrderEvent> received = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && received.size() < 2) {
                ConsumerRecords<String, WorkOrderEvent> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, WorkOrderEvent> record : records) {
                    if (String.valueOf(orderId).equals(record.key())) {
                        received.add(record.value());
                    }
                }
            }

            assertThat(received).hasSize(2);
            assertThat(received.get(0).getEventId()).isEqualTo(event1.getEventId());
            assertThat(received.get(1).getEventId()).isEqualTo(event2.getEventId());

            OutboxEvent saved1 = outboxEventRepository.findById(event1.getId()).orElseThrow();
            OutboxEvent saved2 = outboxEventRepository.findById(event2.getId()).orElseThrow();
            assertThat(saved1.getPublishedAt()).isNotNull();
            assertThat(saved2.getPublishedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("Caso 2: Un evento con payload corrupto bloquea solo su agregado; los de otras ordenes se publican")
    void shouldHaltOnlyCorruptedAggregateAndPublishOthers() {
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1);
        Long corruptOrderId = 2001L;
        Long otherOrderId = 3001L;

        // Evento corrupto para order 2001
        OutboxEvent corruptEvent = new OutboxEvent(
                UUID.randomUUID().toString(),
                corruptOrderId,
                "ORDER_CREATED",
                "{invalid_json_corrupted_payload",
                baseTime
        );
        corruptEvent = outboxEventRepository.save(corruptEvent);

        // Segundo evento para order 2001 (debe ser bloqueado para preservar orden)
        OutboxEvent subsequentEvent = outboxEventRepository.save(
                createValidOutboxEvent(corruptOrderId, "Subsequent Event", baseTime.plusMinutes(2))
        );

        // Evento para order 3001 (debe publicarse con exito)
        OutboxEvent otherEvent = outboxEventRepository.save(
                createValidOutboxEvent(otherOrderId, "Other Aggregate Event", baseTime.plusMinutes(1))
        );

        outboxPublisher.publishPendingEvents();

        OutboxEvent reloadedCorrupt = outboxEventRepository.findById(corruptEvent.getId()).orElseThrow();
        assertThat(reloadedCorrupt.getPublishedAt()).isNull();
        assertThat(reloadedCorrupt.getAttemptCount()).isEqualTo(1);
        assertThat(reloadedCorrupt.getLastError()).isNotNull();

        OutboxEvent reloadedSubsequent = outboxEventRepository.findById(subsequentEvent.getId()).orElseThrow();
        assertThat(reloadedSubsequent.getPublishedAt()).isNull();
        assertThat(reloadedSubsequent.getAttemptCount()).isEqualTo(0);

        OutboxEvent reloadedOther = outboxEventRepository.findById(otherEvent.getId()).orElseThrow();
        assertThat(reloadedOther.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Caso 3: Tras MAX_ATTEMPTS fallos, el evento queda con dead_lettered_at y deja de reintentarse")
    void shouldDeadLetterEventAfterMaxAttempts() {
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1);
        Long orderId = 4001L;

        OutboxEvent failingEvent = new OutboxEvent(
                UUID.randomUUID().toString(),
                orderId,
                "ORDER_CREATED",
                "{corrupt_data",
                baseTime
        );
        failingEvent.setAttemptCount(9);
        failingEvent = outboxEventRepository.save(failingEvent);

        outboxPublisher.publishPendingEvents();

        OutboxEvent deadLettered = outboxEventRepository.findById(failingEvent.getId()).orElseThrow();
        assertThat(deadLettered.getAttemptCount()).isEqualTo(10);
        assertThat(deadLettered.getDeadLetteredAt()).isNotNull();
        assertThat(deadLettered.getPublishedAt()).isNull();

        // Siguiente ejecución: no debe incrementarse ni reintentarse
        outboxPublisher.publishPendingEvents();
        OutboxEvent reloaded = outboxEventRepository.findById(failingEvent.getId()).orElseThrow();
        assertThat(reloaded.getAttemptCount()).isEqualTo(10);
    }
}
