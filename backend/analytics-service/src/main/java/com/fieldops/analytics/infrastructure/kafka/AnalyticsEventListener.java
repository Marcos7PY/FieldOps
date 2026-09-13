package com.fieldops.analytics.infrastructure.kafka;

import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.analytics.application.service.AnalyticsProjectionService;
import com.fieldops.analytics.infrastructure.config.KafkaConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AnalyticsEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventListener.class);
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CONSUMER_GROUP = KafkaConsumerConfig.CONSUMER_GROUP;

    private final AnalyticsProjectionService projectionService;

    public AnalyticsEventListener(AnalyticsProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @KafkaListener(
            id = KafkaConsumerConfig.LISTENER_ID,
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
            if (projectionService.isAlreadyProcessed(eventId, CONSUMER_GROUP)) {
                log.info("Event {} already processed by {}, skipping duplicate", eventId, CONSUMER_GROUP);
                acknowledgment.acknowledge();
                return;
            }

            projectionService.projectEvent(event, CONSUMER_GROUP);
            acknowledgment.acknowledge();
        } finally {
            MDC.remove("traceId");
        }
    }
}
