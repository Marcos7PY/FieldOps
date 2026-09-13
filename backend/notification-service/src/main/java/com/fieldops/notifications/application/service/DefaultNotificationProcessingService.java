package com.fieldops.notifications.application.service;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.notifications.domain.model.NotificationLog;
import com.fieldops.notifications.domain.model.ProcessedEvent;
import com.fieldops.notifications.domain.model.ProcessedEventId;
import com.fieldops.notifications.infrastructure.persistence.NotificationLogRepository;
import com.fieldops.notifications.infrastructure.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DefaultNotificationProcessingService implements NotificationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotificationProcessingService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;

    public DefaultNotificationProcessingService(
            ProcessedEventRepository processedEventRepository,
            NotificationLogRepository notificationLogRepository,
            EmailService emailService
    ) {
        this.processedEventRepository = processedEventRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId, String consumerGroup) {
        return processedEventRepository.existsById(new ProcessedEventId(eventId, consumerGroup));
    }

    @Override
    @Transactional
    public void processAndRecord(WorkOrderEvent event, String consumerGroup) {
        String eventType = event.getEventType();
        String eventId = event.getEventId();
        log.info("Processing event {} for order {} (type={})", eventId, event.getOrderId(), eventType);

        if ("ORDER_ASSIGNED".equals(eventType) && event.getPayload() instanceof OrderAssignedPayload payload) {
            handleOrderAssigned(event, payload);
        } else if ("ORDER_COMPLETED".equals(eventType) && event.getPayload() instanceof OrderCompletedPayload payload) {
            handleOrderCompleted(event, payload);
        } else {
            log.debug("Event type {} ignored by notification service", eventType);
        }

        processedEventRepository.save(new ProcessedEvent(eventId, consumerGroup, LocalDateTime.now()));
    }

    private void handleOrderAssigned(WorkOrderEvent event, OrderAssignedPayload payload) {
        String recipient = payload.getTechnicianEmail();
        String technicianName = payload.getTechnicianName();
        String scheduledAt = payload.getScheduledAt() != null ? payload.getScheduledAt().toString() : "No especificada";

        emailService.sendOrderAssignedNotification(recipient, technicianName, event.getOrderCode(), scheduledAt);
        logNotification(event.getEventId(), recipient, "Nueva orden asignada: " + event.getOrderCode(), "SENT");
    }

    private void handleOrderCompleted(WorkOrderEvent event, OrderCompletedPayload payload) {
        String recipient = "supervisor@fieldops.com";
        String completedAt = payload.getCompletedAt() != null ? payload.getCompletedAt().toString() : "No especificada";

        emailService.sendOrderCompletedNotification(
                recipient,
                event.getOrderCode(),
                payload.getTechnicianId(),
                payload.getDurationMinutes(),
                payload.getEvidenceCount(),
                completedAt
        );
        logNotification(event.getEventId(), recipient, "Orden completada: " + event.getOrderCode(), "SENT");
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
