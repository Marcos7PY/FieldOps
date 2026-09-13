package com.fieldops.analytics.infrastructure.persistence;

import com.fieldops.analytics.domain.model.ProcessedEvent;
import com.fieldops.analytics.domain.model.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    @Modifying
    @Query("DELETE FROM ProcessedEvent p WHERE p.id.consumerGroup = :consumerGroup")
    void deleteByConsumerGroup(@Param("consumerGroup") String consumerGroup);
}
