package com.fieldops.analytics.application.service;

import com.fieldops.analytics.domain.model.ProjectionCheckpoint;
import com.fieldops.analytics.infrastructure.config.KafkaConsumerConfig;
import com.fieldops.analytics.infrastructure.persistence.ProcessedEventRepository;
import com.fieldops.analytics.infrastructure.persistence.ProjectionCheckpointRepository;
import com.fieldops.analytics.infrastructure.persistence.WorkOrderDailyMetricRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class ProjectionResetOperations {

    private final WorkOrderDailyMetricRepository metricRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ProjectionCheckpointRepository checkpointRepository;

    public ProjectionResetOperations(
            WorkOrderDailyMetricRepository metricRepository,
            ProcessedEventRepository processedEventRepository,
            ProjectionCheckpointRepository checkpointRepository
    ) {
        this.metricRepository = metricRepository;
        this.processedEventRepository = processedEventRepository;
        this.checkpointRepository = checkpointRepository;
    }

    @Transactional
    public void truncateReadModelAndClearProcessedEvents() {
        metricRepository.truncateAll();
        processedEventRepository.deleteByConsumerGroup(KafkaConsumerConfig.CONSUMER_GROUP);
    }

    @Transactional
    public void recordRebuildTimestamp() {
        ProjectionCheckpoint checkpoint = checkpointRepository.findById(KafkaConsumerConfig.CONSUMER_GROUP)
                .orElse(new ProjectionCheckpoint(KafkaConsumerConfig.CONSUMER_GROUP, null, null, 0L));
        checkpoint.setRebuiltAt(LocalDateTime.now());
        checkpointRepository.save(checkpoint);
    }
}
