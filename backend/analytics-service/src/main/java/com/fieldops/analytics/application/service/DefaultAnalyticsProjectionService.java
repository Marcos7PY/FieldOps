package com.fieldops.analytics.application.service;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.OrderStartedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.analytics.domain.model.ProcessedEvent;
import com.fieldops.analytics.domain.model.ProcessedEventId;
import com.fieldops.analytics.domain.model.ProjectionCheckpoint;
import com.fieldops.analytics.infrastructure.persistence.ProcessedEventRepository;
import com.fieldops.analytics.infrastructure.persistence.ProjectionCheckpointRepository;
import com.fieldops.analytics.infrastructure.persistence.WorkOrderDailyMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class DefaultAnalyticsProjectionService implements AnalyticsProjectionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAnalyticsProjectionService.class);

    private final WorkOrderDailyMetricRepository metricRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ProjectionCheckpointRepository checkpointRepository;

    public DefaultAnalyticsProjectionService(
            WorkOrderDailyMetricRepository metricRepository,
            ProcessedEventRepository processedEventRepository,
            ProjectionCheckpointRepository checkpointRepository
    ) {
        this.metricRepository = metricRepository;
        this.processedEventRepository = processedEventRepository;
        this.checkpointRepository = checkpointRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId, String consumerGroup) {
        return processedEventRepository.existsById(new ProcessedEventId(eventId, consumerGroup));
    }

    @Override
    @Transactional
    public void projectEvent(WorkOrderEvent event, String consumerGroup) {
        String eventType = event.getEventType();
        String eventId = event.getEventId();
        LocalDate occurredDate = LocalDate.ofInstant(event.getOccurredAt(), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.now();

        log.info("Projecting event {} (type={}) into analytics read model", eventId, eventType);

        switch (eventType) {
            case "ORDER_CREATED" ->
                    metricRepository.upsertMetric(occurredDate, 0L, "DRAFT", 1, null, now);
            case "ORDER_ASSIGNED" -> {
                if (event.getPayload() instanceof OrderAssignedPayload payload) {
                    Long techId = parseTechnicianId(payload.getTechnicianId());
                    metricRepository.upsertMetric(occurredDate, techId, "ASSIGNED", 1, null, now);
                }
            }
            case "ORDER_STARTED" -> {
                if (event.getPayload() instanceof OrderStartedPayload payload) {
                    Long techId = parseTechnicianId(payload.getTechnicianId());
                    metricRepository.upsertMetric(occurredDate, techId, "IN_PROGRESS", 1, null, now);
                }
            }
            case "ORDER_COMPLETED" -> {
                if (event.getPayload() instanceof OrderCompletedPayload payload) {
                    Long techId = parseTechnicianId(payload.getTechnicianId());
                    BigDecimal avgDuration = BigDecimal.valueOf(payload.getDurationMinutes());
                    metricRepository.upsertMetric(occurredDate, techId, "COMPLETED", 1, avgDuration, now);
                }
            }
            case "ORDER_CANCELLED" ->
                    metricRepository.upsertMetric(occurredDate, 0L, "CANCELLED", 1, null, now);
            default ->
                    log.warn("Unknown event type {} for event {}", eventType, eventId);
        }

        // Insert processed_event in same transaction
        processedEventRepository.save(new ProcessedEvent(eventId, consumerGroup, now));

        // Atomic update of checkpoint (F3-T06)
        LocalDateTime occurredDateTime = LocalDateTime.ofInstant(event.getOccurredAt(), ZoneOffset.UTC);
        int updatedRows = checkpointRepository.incrementCheckpoint(consumerGroup, occurredDateTime);
        if (updatedRows == 0) {
            checkpointRepository.save(new ProjectionCheckpoint(consumerGroup, null, occurredDateTime, 1L));
        }
    }

    private Long parseTechnicianId(String technicianId) {
        if (technicianId == null || technicianId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(technicianId);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
