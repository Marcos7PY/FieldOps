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

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.code LIKE :prefix%")
    long countByCodePrefix(@Param("prefix") String prefix);

    @Query("SELECT w.status, COUNT(w) FROM WorkOrder w GROUP BY w.status")
    java.util.List<Object[]> countGroupedByStatus();

    @Query("SELECT w.priority, COUNT(w) FROM WorkOrder w GROUP BY w.priority")
    java.util.List<Object[]> countGroupedByPriority();

    @Query("SELECT w.startedAt, w.completedAt FROM WorkOrder w WHERE w.status = com.fieldops.orders.domain.model.OrderStatus.COMPLETED AND w.startedAt IS NOT NULL AND w.completedAt IS NOT NULL")
    java.util.List<Object[]> findCompletedDurations();
}
