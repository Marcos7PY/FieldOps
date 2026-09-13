package com.fieldops.notifications.infrastructure.kafka;

import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.notifications.application.service.NotificationProcessingService;
import com.fieldops.notifications.domain.model.NotificationLog;
import com.fieldops.notifications.infrastructure.persistence.NotificationLogRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CONSUMER_GROUP = "notification-group";

    private final NotificationProcessingService notificationProcessingService;
    private final NotificationLogRepository notificationLogRepository;

    public NotificationEventListener(
            NotificationProcessingService notificationProcessingService,
            NotificationLogRepository notificationLogRepository
    ) {
        this.notificationProcessingService = notificationProcessingService;
        this.notificationLogRepository = notificationLogRepository;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 4.0, maxDelay = 16000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "true"
    )
    @KafkaListener(
            topics = "fieldops.work-orders.events",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, WorkOrderEvent> record, Acknowledgment acknowledgment) {
        WorkOrderEvent event = record.value();
        if (event == null) {
            acknowledgment.acknowledge();
            return;
        }

        var traceHeader = record.headers().lastHeader(TRACE_ID_HEADER);
        if (traceHeader != null) {
            String traceId = new String(traceHeader.value(), StandardCharsets.UTF_8);
            MDC.put("traceId", traceId);
        }

        try {
            String eventId = event.getEventId();
            if (notificationProcessingService.isAlreadyProcessed(eventId, CONSUMER_GROUP)) {
                log.info("Event {} already processed by {}, discarding duplicate", eventId, CONSUMER_GROUP);
                acknowledgment.acknowledge();
                return;
            }

            notificationProcessingService.processAndRecord(event, CONSUMER_GROUP);
            acknowledgment.acknowledge();
        } finally {
            MDC.remove("traceId");
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, WorkOrderEvent> record,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            Acknowledgment acknowledgment
    ) {
        WorkOrderEvent event = record != null ? record.value() : null;
        String eventId = event != null ? event.getEventId() : "unknown";
        log.error("Event {} deposited in DLT topic {}. Reason: {}", eventId, topic, exceptionMessage);

        if (event != null) {
            NotificationLog dltLog = new NotificationLog(
                    eventId,
                    "DLT",
                    "Event deposited in DLT: " + topic,
                    LocalDateTime.now(),
                    "DLT_FAILED"
            );
            notificationLogRepository.save(dltLog);
        }

        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
