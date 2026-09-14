package com.fieldops.orders.infrastructure.persistence;

import com.fieldops.orders.domain.model.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(Pageable pageable);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(Long aggregateId);
}
