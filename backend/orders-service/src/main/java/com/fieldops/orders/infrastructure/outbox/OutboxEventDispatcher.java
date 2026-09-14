package com.fieldops.orders.infrastructure.outbox;

import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.orders.domain.model.OutboxEvent;
import com.fieldops.orders.infrastructure.persistence.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);
    public static final String TOPIC = "fieldops.work-orders.events";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaOperations<String, Object> kafkaOperations;
    private final WorkOrderEventSerializer serializer;

    public OutboxEventDispatcher(
            OutboxEventRepository outboxEventRepository,
            KafkaOperations<String, Object> kafkaOperations,
            WorkOrderEventSerializer serializer
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaOperations = kafkaOperations;
        this.serializer = serializer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(OutboxEvent event) {
        WorkOrderEvent avroEvent = serializer.fromJson(event.getPayload());
        String partitionKey = String.valueOf(event.getAggregateId());

        String traceId = MDC.get("traceId");
        boolean mdcGenerated = false;
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
            mdcGenerated = true;
        }

        ProducerRecord<String, Object> record = new ProducerRecord<>(TOPIC, partitionKey, avroEvent);
        record.headers().add(TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));

        try {
            kafkaOperations.send(record).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación interrumpida para el evento " + event.getEventId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("El broker no confirmó el evento " + event.getEventId(), e);
        } finally {
            if (mdcGenerated) {
                MDC.remove("traceId");
            }
        }

        event.setPublishedAt(LocalDateTime.now());
        outboxEventRepository.save(event);

        log.info("Published outbox event {} (type={}, orderId={}) to topic {}",
                event.getEventId(), event.getEventType(), partitionKey, TOPIC);
    }
}
