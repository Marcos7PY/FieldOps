package com.fieldops.orders.infrastructure.persistence;

import com.fieldops.orders.domain.model.WorkOrderEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderEvidenceRepository extends JpaRepository<WorkOrderEvidence, Long> {

    List<WorkOrderEvidence> findByWorkOrderId(Long workOrderId);

    boolean existsByWorkOrderId(Long workOrderId);

    long countByWorkOrderId(Long workOrderId);
}
