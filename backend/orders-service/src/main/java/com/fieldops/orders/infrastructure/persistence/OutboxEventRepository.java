package com.fieldops.orders.infrastructure.persistence;

import com.fieldops.orders.domain.model.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAscIdAsc(Pageable pageable);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(Long aggregateId);

    @Modifying
    @Transactional
    @Query("""
            UPDATE OutboxEvent e
               SET e.attemptCount = e.attemptCount + 1,
                   e.lastError = :error,
                   e.deadLetteredAt = CASE WHEN e.attemptCount + 1 >= :maxAttempts THEN :now ELSE e.deadLetteredAt END
             WHERE e.id = :id
            """)
    void markFailure(@Param("id") Long id,
                     @Param("error") String error,
                     @Param("maxAttempts") int maxAttempts,
                     @Param("now") LocalDateTime now);
}
