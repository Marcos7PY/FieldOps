package com.fieldops.notifications.application.service;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.notifications.domain.model.ProcessedEventId;
import com.fieldops.notifications.infrastructure.client.UserDirectoryClient;
import com.fieldops.notifications.infrastructure.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DefaultNotificationProcessingService implements NotificationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotificationProcessingService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogWriter notificationLogWriter;
    private final EmailService emailService;
    private final UserDirectoryClient userDirectoryClient;

    public DefaultNotificationProcessingService(
            ProcessedEventRepository processedEventRepository,
            NotificationLogWriter notificationLogWriter,
            EmailService emailService,
            UserDirectoryClient userDirectoryClient
    ) {
        this.processedEventRepository = processedEventRepository;
        this.notificationLogWriter = notificationLogWriter;
        this.emailService = emailService;
        this.userDirectoryClient = userDirectoryClient;
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

        NotificationPlan plan;
        try {
            plan = buildPlan(event);
        } catch (IllegalStateException ex) {
            log.warn("Skipping notification for event {}: {}", eventId, ex.getMessage());
            notificationLogWriter.recordSkipped(eventId, ex.getMessage());
            notificationLogWriter.markProcessed(eventId, consumerGroup);
            return;
        }

        if (plan == null) {
            log.debug("Event type {} ignored by notification service", eventType);
            notificationLogWriter.markProcessed(eventId, consumerGroup);
            return;
        }

        Long logId = notificationLogWriter.recordAttempt(plan, eventId);

        try {
            emailService.send(plan);
            notificationLogWriter.markSent(logId);
        } catch (Exception e) {
            log.error("Failed to send notification for event {}: {}", eventId, e.getMessage(), e);
            notificationLogWriter.markFailed(logId, e.getMessage());
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Failed to send notification email", e);
        }

        notificationLogWriter.markProcessed(eventId, consumerGroup);
    }

    private NotificationPlan buildPlan(WorkOrderEvent event) {
        String eventType = event.getEventType();
        if ("ORDER_ASSIGNED".equals(eventType) && event.getPayload() instanceof OrderAssignedPayload payload) {
            return buildOrderAssignedPlan(event, payload);
        } else if ("ORDER_COMPLETED".equals(eventType) && event.getPayload() instanceof OrderCompletedPayload payload) {
            return buildOrderCompletedPlan(event, payload);
        }
        return null;
    }

    private NotificationPlan buildOrderAssignedPlan(WorkOrderEvent event, OrderAssignedPayload payload) {
        String technicianIdStr = payload.getTechnicianId();
        String recipient = payload.getTechnicianEmail();
        String technicianName = payload.getTechnicianName();

        if (technicianIdStr != null && !technicianIdStr.isBlank()) {
            try {
                Long techId = Long.parseLong(technicianIdStr);
                Optional<UserDirectoryClient.UserDto> userOpt = userDirectoryClient.findUserById(techId);
                if (userOpt.isPresent()) {
                    UserDirectoryClient.UserDto user = userOpt.get();
                    if (user.email() != null && !user.email().isBlank()) {
                        recipient = user.email();
                    }
                    if (user.fullName() != null && !user.fullName().isBlank()) {
                        technicianName = user.fullName();
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("Recipient email could not be resolved for technician " + technicianIdStr);
        }
        if (technicianName == null || technicianName.isBlank()) {
            technicianName = "Tecnico #" + technicianIdStr;
        }

        String scheduledAt = payload.getScheduledAt() != null ? payload.getScheduledAt().toString() : "No especificada";
        return new OrderAssignedPlan(recipient, technicianName, event.getOrderCode(), scheduledAt);
    }

    private NotificationPlan buildOrderCompletedPlan(WorkOrderEvent event, OrderCompletedPayload payload) {
        String recipient = null;
        String createdBy = payload.getCreatedBy();

        if (createdBy != null && !createdBy.isBlank()) {
            try {
                Long createdById = Long.parseLong(createdBy);
                Optional<UserDirectoryClient.UserDto> userOpt = userDirectoryClient.findUserById(createdById);
                if (userOpt.isPresent() && userOpt.get().email() != null && !userOpt.get().email().isBlank()) {
                    recipient = userOpt.get().email();
                }
            } catch (NumberFormatException ignored) {
                if (createdBy.contains("@")) {
                    recipient = createdBy;
                }
            }
        }

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("Recipient email could not be resolved for createdBy " + createdBy);
        }

        String completedAt = payload.getCompletedAt() != null ? payload.getCompletedAt().toString() : "No especificada";
        return new OrderCompletedPlan(
                recipient,
                event.getOrderCode(),
                payload.getTechnicianId(),
                payload.getDurationMinutes(),
                payload.getEvidenceCount(),
                completedAt
        );
    }
}
