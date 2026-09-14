package com.fieldops.analytics.application.service;

import com.fieldops.analytics.domain.exception.RebuildAlreadyInProgressException;
import com.fieldops.analytics.infrastructure.config.KafkaConsumerConfig;
import com.fieldops.analytics.infrastructure.kafka.KafkaOffsetResetter;
import com.fieldops.analytics.infrastructure.persistence.ProjectionCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.ListenerContainerRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    private ProjectionResetOperations resetOperations;

    @Mock
    private ProjectionCheckpointRepository checkpointRepository;

    private DefaultProjectionRebuildService rebuildService;

    @BeforeEach
    void setUp() {
        rebuildService = new DefaultProjectionRebuildService(
                listenerEndpointRegistry,
                kafkaOffsetResetter,
                resetOperations,
                checkpointRepository
        );
    }

    @Test
    @DisplayName("F3-T03: Delega en ProjectionResetOperations y ejecuta el proceso de rebuild completo")
    void shouldExecuteFullRebuildProcess() {
        when(listenerEndpointRegistry.getListenerContainer(KafkaConsumerConfig.LISTENER_ID))
                .thenReturn(listenerContainer);
        when(listenerContainer.isRunning()).thenReturn(true);

        rebuildService.executeRebuild();

        // 1. Detuvo el contenedor
        verify(listenerContainer).stop();

        // 2. Truncó modelo de lectura y limpió eventos vía ProjectionResetOperations
        verify(resetOperations).truncateReadModelAndClearProcessedEvents();

        // 3. Reseteó offsets a earliest
        verify(kafkaOffsetResetter).resetConsumerGroupToEarliest(
                eq(KafkaConsumerConfig.CONSUMER_GROUP),
                eq("fieldops.work-orders.events")
        );

        // 4. Registró timestamp de rebuild vía ProjectionResetOperations
        verify(resetOperations).recordRebuildTimestamp();

        // 5. Reinició el contenedor
        verify(listenerContainer).start();
    }

    @Test
    void shouldRebuildProjectionAsync() {
        when(listenerEndpointRegistry.getListenerContainer(KafkaConsumerConfig.LISTENER_ID))
                .thenReturn(listenerContainer);
        when(listenerContainer.isRunning()).thenReturn(false);

        CompletableFuture<Void> future = rebuildService.rebuildProjectionAsync();
        future.join();

        verify(resetOperations).truncateReadModelAndClearProcessedEvents();
        verify(kafkaOffsetResetter).resetConsumerGroupToEarliest(
                eq(KafkaConsumerConfig.CONSUMER_GROUP),
                eq("fieldops.work-orders.events")
        );
        verify(listenerContainer).start();
    }

    @Test
    @DisplayName("F3-T04: Dos llamadas concurrentes lanzan RebuildAlreadyInProgressException")
    void shouldRejectConcurrentRebuildCalls() {
        when(listenerEndpointRegistry.getListenerContainer(KafkaConsumerConfig.LISTENER_ID))
                .thenReturn(listenerContainer);
        when(listenerContainer.isRunning()).thenReturn(false);

        // Simulamos una reconstrucción que tarda un momento
        doAnswer(inv -> {
            Thread.sleep(100);
            return null;
        }).when(resetOperations).truncateReadModelAndClearProcessedEvents();

        CompletableFuture<Void> first = rebuildService.rebuildProjectionAsync();

        assertThatThrownBy(() -> rebuildService.rebuildProjectionAsync())
                .isInstanceOf(RebuildAlreadyInProgressException.class)
                .hasMessageContaining("Ya hay una reconstrucción de proyección en curso");

        first.join();

        // Tras completar, una nueva llamada debe ser aceptada
        CompletableFuture<Void> next = rebuildService.rebuildProjectionAsync();
        next.join();
    }
}
