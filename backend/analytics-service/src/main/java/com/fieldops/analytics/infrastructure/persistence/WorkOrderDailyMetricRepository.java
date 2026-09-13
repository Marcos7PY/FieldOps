package com.fieldops.analytics.infrastructure.persistence;

import com.fieldops.analytics.domain.model.WorkOrderDailyMetric;
import com.fieldops.analytics.domain.model.WorkOrderDailyMetricId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WorkOrderDailyMetricRepository extends JpaRepository<WorkOrderDailyMetric, WorkOrderDailyMetricId>, WorkOrderDailyMetricRepositoryCustom {

    @Query("SELECT m FROM WorkOrderDailyMetric m WHERE (:from IS NULL OR m.id.metricDate >= :from) AND (:to IS NULL OR m.id.metricDate <= :to) ORDER BY m.id.metricDate ASC")
    List<WorkOrderDailyMetric> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT m FROM WorkOrderDailyMetric m WHERE m.id.technicianId > 0 ORDER BY m.id.technicianId ASC")
    List<WorkOrderDailyMetric> findAllTechnicianMetrics();
}
