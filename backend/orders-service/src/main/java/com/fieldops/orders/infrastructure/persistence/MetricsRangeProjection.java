package com.fieldops.orders.infrastructure.persistence;

import java.math.BigDecimal;

public interface MetricsRangeProjection {
    long getTotalOrders();
    long getDraftCount();
    long getAssignedCount();
    long getInProgressCount();
    long getCompletedCount();
    long getCancelledCount();
    long getLowCount();
    long getMediumCount();
    long getHighCount();
    long getCriticalCount();
    BigDecimal getAvgDurationMinutes();
}
