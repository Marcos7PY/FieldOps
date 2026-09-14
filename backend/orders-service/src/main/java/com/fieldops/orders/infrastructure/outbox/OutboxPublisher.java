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

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "fieldops.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_ATTEMPTS = 10;

    private static String truncate(String msg) {
        if (msg == null) return "unknown";
        return msg.length() <= 500 ? msg : msg.substring(0, 500);
    }

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
    @SchedulerLock(name = "outbox_publisher_lock", lockAtLeastFor = "1s", lockAtMostFor = "180s")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscIdAsc(PageRequest.of(0, 10));
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pendingEvents.size());

        java.util.Set<Long> blockedAggregates = new java.util.HashSet<>();
        for (OutboxEvent event : pendingEvents) {
            if (event.getAggregateId() != null && blockedAggregates.contains(event.getAggregateId())) {
                continue;
            }
            try {
                dispatcher.dispatch(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id: {}, aggregateId: {}, attempt {}: {}",
                        event.getEventId(), event.getAggregateId(), event.getAttemptCount() + 1, e.getMessage(), e);
                outboxEventRepository.markFailure(event.getId(), truncate(e.getMessage()), MAX_ATTEMPTS, LocalDateTime.now());
                if (event.getAggregateId() != null) {
                    blockedAggregates.add(event.getAggregateId());
                }
            }
        }
    }
}
