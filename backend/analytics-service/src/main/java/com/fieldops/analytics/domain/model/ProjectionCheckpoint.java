package com.fieldops.analytics.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "projection_checkpoint")
public class ProjectionCheckpoint {

    @Id
    @Column(name = "consumer_group", length = 50, nullable = false)
    private String consumerGroup;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    @Column(name = "rebuilt_at")
    private LocalDateTime rebuiltAt;

    @Column(name = "events_processed", nullable = false)
    private long eventsProcessed;

    public ProjectionCheckpoint() {
    }

    public ProjectionCheckpoint(String consumerGroup, LocalDateTime lastEventAt, LocalDateTime rebuiltAt, long eventsProcessed) {
        this.consumerGroup = consumerGroup;
        this.lastEventAt = lastEventAt;
        this.rebuiltAt = rebuiltAt;
        this.eventsProcessed = eventsProcessed;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public LocalDateTime getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(LocalDateTime lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public LocalDateTime getRebuiltAt() {
        return rebuiltAt;
    }

    public void setRebuiltAt(LocalDateTime rebuiltAt) {
        this.rebuiltAt = rebuiltAt;
    }

    public long getEventsProcessed() {
        return eventsProcessed;
    }

    public void setEventsProcessed(long eventsProcessed) {
        this.eventsProcessed = eventsProcessed;
    }
}
