package com.fieldops.orders.infrastructure.outbox;

import com.fieldops.orders.domain.model.OutboxEvent;
import com.fieldops.orders.infrastructure.persistence.OutboxEventRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "fieldops.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventDispatcher dispatcher;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            OutboxEventDispatcher dispatcher
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 2000)
    @SchedulerLock(name = "outbox_publisher_lock", lockAtLeastFor = "1s", lockAtMostFor = "10s")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 100));
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                dispatcher.dispatch(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id: {}, partition key will halt for ordering: {}",
                        event.getEventId(), e.getMessage(), e);
                break;
            }
        }
    }
}
