package com.fieldops.analytics.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_order_daily_metrics")
public class WorkOrderDailyMetric {

    @EmbeddedId
    private WorkOrderDailyMetricId id;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "avg_duration_minutes", precision = 10, scale = 2)
    private BigDecimal avgDurationMinutes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public WorkOrderDailyMetric() {
    }

    public WorkOrderDailyMetric(WorkOrderDailyMetricId id, int orderCount, BigDecimal avgDurationMinutes, LocalDateTime updatedAt) {
        this.id = id;
        this.orderCount = orderCount;
        this.avgDurationMinutes = avgDurationMinutes;
        this.updatedAt = updatedAt;
    }

    public WorkOrderDailyMetric(LocalDate metricDate, Long technicianId, String status, int orderCount, BigDecimal avgDurationMinutes, LocalDateTime updatedAt) {
        this.id = new WorkOrderDailyMetricId(metricDate, technicianId, status);
        this.orderCount = orderCount;
        this.avgDurationMinutes = avgDurationMinutes;
        this.updatedAt = updatedAt;
    }

    public WorkOrderDailyMetricId getId() {
        return id;
    }

    public void setId(WorkOrderDailyMetricId id) {
        this.id = id;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(int orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getAvgDurationMinutes() {
        return avgDurationMinutes;
    }

    public void setAvgDurationMinutes(BigDecimal avgDurationMinutes) {
        this.avgDurationMinutes = avgDurationMinutes;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
