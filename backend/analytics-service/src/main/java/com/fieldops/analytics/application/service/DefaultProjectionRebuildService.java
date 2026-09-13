package com.fieldops.analytics.application.service;

import com.fieldops.analytics.domain.model.ProjectionCheckpoint;
import com.fieldops.analytics.infrastructure.config.KafkaConsumerConfig;
import com.fieldops.analytics.infrastructure.kafka.KafkaOffsetResetter;
import com.fieldops.analytics.infrastructure.persistence.ProcessedEventRepository;
import com.fieldops.analytics.infrastructure.persistence.ProjectionCheckpointRepository;
import com.fieldops.analytics.infrastructure.persistence.WorkOrderDailyMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.listener.ListenerContainerRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
public class DefaultProjectionRebuildService implements ProjectionRebuildService {

    private static final Logger log = LoggerFactory.getLogger(DefaultProjectionRebuildService.class);
    private static final String TOPIC = "fieldops.work-orders.events";

    private final ListenerContainerRegistry listenerEndpointRegistry;
    private final KafkaOffsetResetter kafkaOffsetResetter;
    private final WorkOrderDailyMetricRepository metricRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ProjectionCheckpointRepository checkpointRepository;
    private final TaskExecutor taskExecutor;

    public DefaultProjectionRebuildService(
            ListenerContainerRegistry listenerEndpointRegistry,
            KafkaOffsetResetter kafkaOffsetResetter,
            WorkOrderDailyMetricRepository metricRepository,
            ProcessedEventRepository processedEventRepository,
            ProjectionCheckpointRepository checkpointRepository
    ) {
        this.listenerEndpointRegistry = listenerEndpointRegistry;
        this.kafkaOffsetResetter = kafkaOffsetResetter;
        this.metricRepository = metricRepository;
        this.processedEventRepository = processedEventRepository;
        this.checkpointRepository = checkpointRepository;
        this.taskExecutor = new SimpleAsyncTaskExecutor("rebuild-projection-");
    }

    @Override
    public CompletableFuture<Void> rebuildProjectionAsync() {
        return CompletableFuture.runAsync(this::executeRebuild, taskExecutor);
    }

    @Override
    public void executeRebuild() {
        log.info("Initiating full projection rebuild for {}", KafkaConsumerConfig.CONSUMER_GROUP);

        MessageListenerContainer container = listenerEndpointRegistry.getListenerContainer(KafkaConsumerConfig.LISTENER_ID);
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("Stopped listener container {}", KafkaConsumerConfig.LISTENER_ID);
        }

        try {
            truncateReadModelAndClearProcessedEvents();

            kafkaOffsetResetter.resetConsumerGroupToEarliest(KafkaConsumerConfig.CONSUMER_GROUP, TOPIC);
            log.info("Reset consumer group offset to earliest for topic {}", TOPIC);

            recordRebuildTimestamp();
            log.info("Projection rebuild setup complete for {}", KafkaConsumerConfig.CONSUMER_GROUP);
        } catch (Exception e) {
            log.error("Error during projection rebuild: {}", e.getMessage(), e);
            throw new RuntimeException("Error during projection rebuild: " + e.getMessage(), e);
        } finally {
            if (container != null) {
                container.start();
                log.info("Resumed listener container {}", KafkaConsumerConfig.LISTENER_ID);
            }
        }
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
