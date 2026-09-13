package com.fieldops.notifications.infrastructure.kafka;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.notifications.application.service.EmailService;
import com.fieldops.notifications.domain.model.NotificationLog;
import com.fieldops.notifications.infrastructure.persistence.NotificationLogRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final EmailService emailService;
    private final NotificationLogRepository notificationLogRepository;

    public NotificationEventListener(
            EmailService emailService,
            NotificationLogRepository notificationLogRepository
    ) {
        this.emailService = emailService;
        this.notificationLogRepository = notificationLogRepository;
    }

    @KafkaListener(
            topics = "fieldops.work-orders.events",
            groupId = "notification-group",
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
            processEvent(event);
            acknowledgment.acknowledge();
        } finally {
            MDC.remove("traceId");
        }
    }

    public void processEvent(WorkOrderEvent event) {
        String eventType = event.getEventType();
        log.info("Received event {} for order {} (type={})", event.getEventId(), event.getOrderId(), eventType);

        if ("ORDER_ASSIGNED".equals(eventType) && event.getPayload() instanceof OrderAssignedPayload payload) {
            handleOrderAssigned(event, payload);
        } else if ("ORDER_COMPLETED".equals(eventType) && event.getPayload() instanceof OrderCompletedPayload payload) {
            handleOrderCompleted(event, payload);
        } else {
            log.debug("Event type {} ignored by notification service", eventType);
        }
    }

    private void handleOrderAssigned(WorkOrderEvent event, OrderAssignedPayload payload) {
        String recipient = payload.getTechnicianEmail();
        String technicianName = payload.getTechnicianName();
        String scheduledAt = payload.getScheduledAt() != null ? payload.getScheduledAt().toString() : "No especificada";

        try {
            emailService.sendOrderAssignedNotification(recipient, technicianName, event.getOrderCode(), scheduledAt);
            logNotification(event.getEventId(), recipient, "Nueva orden asignada: " + event.getOrderCode(), "SENT");
        } catch (Exception e) {
            logNotification(event.getEventId(), recipient, "Nueva orden asignada: " + event.getOrderCode(), "FAILED");
            throw e;
        }
    }

    private void handleOrderCompleted(WorkOrderEvent event, OrderCompletedPayload payload) {
        String recipient = "supervisor@fieldops.com";
        String completedAt = payload.getCompletedAt() != null ? payload.getCompletedAt().toString() : "No especificada";

        try {
            emailService.sendOrderCompletedNotification(
                    recipient,
                    event.getOrderCode(),
                    payload.getTechnicianId(),
                    payload.getDurationMinutes(),
                    payload.getEvidenceCount(),
                    completedAt
            );
            logNotification(event.getEventId(), recipient, "Orden completada: " + event.getOrderCode(), "SENT");
        } catch (Exception e) {
            logNotification(event.getEventId(), recipient, "Orden completada: " + event.getOrderCode(), "FAILED");
            throw e;
        }
    }

    private void logNotification(String eventId, String recipient, String subject, String status) {
        NotificationLog notificationLog = new NotificationLog(
                eventId,
                recipient,
                subject,
                LocalDateTime.now(),
                status
        );
        notificationLogRepository.save(notificationLog);
    }
}
