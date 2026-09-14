package com.fieldops.orders.infrastructure.persistence;

import com.fieldops.orders.domain.model.WorkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    @Override
    @EntityGraph(attributePaths = {"client"})
    Page<WorkOrder> findAll(@Nullable Specification<WorkOrder> spec, Pageable pageable);

    Optional<WorkOrder> findByCode(String code);

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.code LIKE CONCAT(:prefix, '%')")
    long countByCodePrefix(@Param("prefix") String prefix);

    @Query("SELECT w.status, COUNT(w) FROM WorkOrder w GROUP BY w.status")
    java.util.List<Object[]> countGroupedByStatus();

    @Query("SELECT w.priority, COUNT(w) FROM WorkOrder w GROUP BY w.priority")
    java.util.List<Object[]> countGroupedByPriority();

    @Query(value = """
            SELECT AVG(CAST(DATEDIFF(MINUTE, started_at, completed_at) AS DECIMAL(18,4)))
            FROM work_order
            WHERE status = 'COMPLETED'
              AND started_at IS NOT NULL
              AND completed_at IS NOT NULL
              AND completed_at >= started_at
            """, nativeQuery = true)
    java.math.BigDecimal findAverageCompletionMinutes();

    @Query(value = """
            SELECT
                COUNT(*) AS totalOrders,
                COUNT(CASE WHEN status = 'DRAFT'       THEN 1 END) AS draftCount,
                COUNT(CASE WHEN status = 'ASSIGNED'    THEN 1 END) AS assignedCount,
                COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS inProgressCount,
                COUNT(CASE WHEN status = 'COMPLETED'   THEN 1 END) AS completedCount,
                COUNT(CASE WHEN status = 'CANCELLED'   THEN 1 END) AS cancelledCount,
                COUNT(CASE WHEN priority = 'LOW'      THEN 1 END) AS lowCount,
                COUNT(CASE WHEN priority = 'MEDIUM'   THEN 1 END) AS mediumCount,
                COUNT(CASE WHEN priority = 'HIGH'     THEN 1 END) AS highCount,
                COUNT(CASE WHEN priority = 'CRITICAL' THEN 1 END) AS criticalCount,
                CAST(AVG(CASE
                    WHEN status = 'COMPLETED'
                         AND started_at IS NOT NULL
                         AND completed_at IS NOT NULL
                         AND completed_at >= started_at
                    THEN CAST(DATEDIFF(MINUTE, started_at, completed_at) AS DECIMAL(18,4))
                END) AS DECIMAL(18,4)) AS avgDurationMinutes
            FROM work_order
            WHERE created_at >= :from
              AND created_at <  :toExclusive
            """, nativeQuery = true)
    MetricsRangeProjection findMetricsInRange(@Param("from") java.time.LocalDateTime from,
                                              @Param("toExclusive") java.time.LocalDateTime toExclusive);
}
