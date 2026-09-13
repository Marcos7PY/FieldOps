package com.fieldops.analytics.infrastructure.persistence;

import com.fieldops.analytics.domain.model.ProcessedEvent;
import com.fieldops.analytics.domain.model.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
