package com.fieldops.analytics.application.service;

import com.fieldops.analytics.api.dto.RebuildStatusResponse;
import com.fieldops.analytics.domain.exception.RebuildAlreadyInProgressException;
import com.fieldops.analytics.domain.model.ProjectionCheckpoint;
import com.fieldops.analytics.infrastructure.config.KafkaConsumerConfig;
import com.fieldops.analytics.infrastructure.kafka.KafkaOffsetResetter;
import com.fieldops.analytics.infrastructure.persistence.ProjectionCheckpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ListenerContainerRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DefaultProjectionRebuildService implements ProjectionRebuildService {

    private static final Logger log = LoggerFactory.getLogger(DefaultProjectionRebuildService.class);
    private static final String TOPIC = "fieldops.work-orders.events";

    private final ListenerContainerRegistry listenerEndpointRegistry;
    private final KafkaOffsetResetter kafkaOffsetResetter;
    private final ProjectionResetOperations resetOperations;
    private final ProjectionCheckpointRepository checkpointRepository;

    private final AtomicBoolean rebuildInProgress = new AtomicBoolean(false);
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "rebuild-projection"));

    public DefaultProjectionRebuildService(
            ListenerContainerRegistry listenerEndpointRegistry,
            KafkaOffsetResetter kafkaOffsetResetter,
            ProjectionResetOperations resetOperations,
            ProjectionCheckpointRepository checkpointRepository
    ) {
        this.listenerEndpointRegistry = listenerEndpointRegistry;
        this.kafkaOffsetResetter = kafkaOffsetResetter;
        this.resetOperations = resetOperations;
        this.checkpointRepository = checkpointRepository;
    }

    @Override
    public CompletableFuture<Void> rebuildProjectionAsync() {
        if (!rebuildInProgress.compareAndSet(false, true)) {
            throw new RebuildAlreadyInProgressException(
                    "Ya hay una reconstrucción de proyección en curso");
        }
        return CompletableFuture.runAsync(this::executeRebuild, executor)
                .whenComplete((v, t) -> rebuildInProgress.set(false));
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
            resetOperations.truncateReadModelAndClearProcessedEvents();

            kafkaOffsetResetter.resetConsumerGroupToEarliest(KafkaConsumerConfig.CONSUMER_GROUP, TOPIC);
            log.info("Reset consumer group offset to earliest for topic {}", TOPIC);

            resetOperations.recordRebuildTimestamp();
            log.info("Projection rebuild setup complete for {}", KafkaConsumerConfig.CONSUMER_GROUP);
        } catch (Exception e) {
            log.error("Error during projection rebuild: {}", e.getMessage(), e);
            throw new IllegalStateException("Error during projection rebuild: " + e.getMessage(), e);
        } finally {
            if (container != null) {
                container.start();
                log.info("Resumed listener container {}", KafkaConsumerConfig.LISTENER_ID);
            }
        }
    }

    @Override
    public RebuildStatusResponse getRebuildStatus() {
        boolean inProgress = rebuildInProgress.get();
        LocalDateTime lastRebuiltAt = checkpointRepository.findById(KafkaConsumerConfig.CONSUMER_GROUP)
                .map(ProjectionCheckpoint::getRebuiltAt)
                .orElse(null);
        return new RebuildStatusResponse(inProgress, lastRebuiltAt);
    }
}
