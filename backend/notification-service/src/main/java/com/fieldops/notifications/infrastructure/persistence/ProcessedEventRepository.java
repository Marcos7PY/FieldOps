package com.fieldops.notifications.infrastructure.persistence;

import com.fieldops.notifications.domain.model.ProcessedEvent;
import com.fieldops.notifications.domain.model.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
