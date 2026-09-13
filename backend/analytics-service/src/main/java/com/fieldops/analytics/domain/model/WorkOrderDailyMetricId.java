package com.fieldops.analytics.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class WorkOrderDailyMetricId implements Serializable {

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "technician_id", nullable = false)
    private Long technicianId;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    public WorkOrderDailyMetricId() {
    }

    public WorkOrderDailyMetricId(LocalDate metricDate, Long technicianId, String status) {
        this.metricDate = metricDate;
        this.technicianId = technicianId;
        this.status = status;
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public void setMetricDate(LocalDate metricDate) {
        this.metricDate = metricDate;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkOrderDailyMetricId that = (WorkOrderDailyMetricId) o;
        return Objects.equals(metricDate, that.metricDate)
                && Objects.equals(technicianId, that.technicianId)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricDate, technicianId, status);
    }
}
