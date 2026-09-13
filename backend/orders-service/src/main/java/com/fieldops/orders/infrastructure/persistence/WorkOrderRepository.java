package com.fieldops.orders.infrastructure.persistence;

import com.fieldops.orders.domain.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    Optional<WorkOrder> findByCode(String code);

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.code LIKE :prefix%")
    long countByCodePrefix(@Param("prefix") String prefix);
}