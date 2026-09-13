package com.fieldops.analytics.application.service;

import com.fieldops.analytics.domain.model.ProjectionCheckpoint;
import com.fieldops.analytics.infrastructure.config.KafkaConsumerConfig;
import com.fieldops.analytics.infrastructure.kafka.KafkaOffsetResetter;
import com.fieldops.analytics.infrastructure.persistence.ProcessedEventRepository;
import com.fieldops.analytics.infrastructure.persistence.ProjectionCheckpointRepository;
import com.fieldops.analytics.infrastructure.persistence.WorkOrderDailyMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.ListenerContainerRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProjectionRebuildServiceTest {

    @Mock
    private ListenerContainerRegistry listenerEndpointRegistry;

    @Mock
    private MessageListenerContainer listenerContainer;

    @Mock
    private KafkaOffsetResetter kafkaOffsetResetter;

    @Mock
    private WorkOrderDailyMetricRepository metricRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ProjectionCheckpointRepository checkpointRepository;

    private DefaultProjectionRebuildService rebuildService;

    @BeforeEach
    void setUp() {
        rebuildService = new DefaultProjectionRebuildService(
                listenerEndpointRegistry,
                kafkaOffsetResetter,
                metricRepository,
                processedEventRepository,
                checkpointRepository
        );
    }

    @Test
    void shouldExecuteFullRebuildProcess() {
        when(listenerEndpointRegistry.getListenerContainer(KafkaConsumerConfig.LISTENER_ID))
                .thenReturn(listenerContainer);
        when(listenerContainer.isRunning()).thenReturn(true);
        when(checkpointRepository.findById(KafkaConsumerConfig.CONSUMER_GROUP)).thenReturn(Optional.empty());

        rebuildService.executeRebuild();

        // 1. Stopped container
        verify(listenerContainer).stop();

        // 2. Truncated read model & deleted processed events
        verify(metricRepository).truncateAll();
        verify(processedEventRepository).deleteByConsumerGroup(KafkaConsumerConfig.CONSUMER_GROUP);

        // 3. Reset offset
        verify(kafkaOffsetResetter).resetConsumerGroupToEarliest(
                eq(KafkaConsumerConfig.CONSUMER_GROUP),
                eq("fieldops.work-orders.events")
        );

        // 4. Saved checkpoint
        ArgumentCaptor<ProjectionCheckpoint> checkpointCaptor = ArgumentCaptor.forClass(ProjectionCheckpoint.class);
        verify(checkpointRepository).save(checkpointCaptor.capture());
        assertThat(checkpointCaptor.getValue().getRebuiltAt()).isNotNull();

        // 5. Restarted container
        verify(listenerContainer).start();
    }

    @Test
    void shouldRebuildProjectionAsync() {
        when(listenerEndpointRegistry.getListenerContainer(KafkaConsumerConfig.LISTENER_ID))
                .thenReturn(listenerContainer);
        when(listenerContainer.isRunning()).thenReturn(false);
        when(checkpointRepository.findById(KafkaConsumerConfig.CONSUMER_GROUP)).thenReturn(Optional.empty());

        CompletableFuture<Void> future = rebuildService.rebuildProjectionAsync();
        future.join();

        verify(metricRepository).truncateAll();
        verify(kafkaOffsetResetter).resetConsumerGroupToEarliest(
                eq(KafkaConsumerConfig.CONSUMER_GROUP),
                eq("fieldops.work-orders.events")
        );
        verify(listenerContainer).start();
    }
}
