package com.fieldops.analytics.infrastructure.persistence;

import com.fieldops.analytics.domain.model.ProjectionCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ProjectionCheckpointRepository extends JpaRepository<ProjectionCheckpoint, String> {

    @Modifying
    @Query(value = """
            UPDATE projection_checkpoint
            SET events_processed = events_processed + 1,
                last_event_at = CASE
                    WHEN last_event_at IS NULL OR last_event_at < :occurredAt THEN :occurredAt
                    ELSE last_event_at
                END
            WHERE consumer_group = :consumerGroup
            """, nativeQuery = true)
    int incrementCheckpoint(@Param("consumerGroup") String consumerGroup,
                            @Param("occurredAt") LocalDateTime occurredAt);
}
