package com.fieldops.analytics.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface WorkOrderDailyMetricRepositoryCustom {

    void upsertMetric(LocalDate metricDate, Long technicianId, String status, int count, BigDecimal avgDuration, LocalDateTime updatedAt);

    void truncateAll();
}
