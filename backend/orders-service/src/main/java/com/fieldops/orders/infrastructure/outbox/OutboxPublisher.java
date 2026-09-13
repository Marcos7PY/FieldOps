package com.fieldops.orders.infrastructure.outbox;

import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.orders.domain.model.OutboxEvent;
import com.fieldops.orders.infrastructure.persistence.OutboxEventRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "fieldops.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    public static final String TOPIC = "fieldops.work-orders.events";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final OutboxEventRepository outboxEventRepository;
    private final org.springframework.kafka.core.KafkaOperations<String, Object> kafkaOperations;
    private final WorkOrderEventSerializer serializer;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            org.springframework.kafka.core.KafkaOperations<String, Object> kafkaOperations,
            WorkOrderEventSerializer serializer
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaOperations = kafkaOperations;
        this.serializer = serializer;
    }

    @Scheduled(fixedDelay = 2000)
    @SchedulerLock(name = "outbox_publisher_lock", lockAtLeastFor = "1s", lockAtMostFor = "10s")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 100));
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                publishSingleEvent(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id: {}, partition key will halt for ordering: {}",
                        event.getEventId(), e.getMessage(), e);
                break;
            }
        }
    }

    @Transactional
    public void publishSingleEvent(OutboxEvent event) {
        WorkOrderEvent avroEvent = serializer.fromJson(event.getPayload());
        String partitionKey = String.valueOf(event.getAggregateId());

        String traceId = MDC.get("traceId");
        boolean mdcGenerated = false;
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
            mdcGenerated = true;
        }

        ProducerRecord<String, Object> record = new ProducerRecord<>(TOPIC, partitionKey, avroEvent);

        try {
            record.headers().add(TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
            kafkaOperations.send(record);

            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);

            log.info("Published outbox event {} (type={}, orderId={}) to topic {}",
                    event.getEventId(), event.getEventType(), partitionKey, TOPIC);
        } finally {
            if (mdcGenerated) {
                MDC.remove("traceId");
            }
        }
    }
}
