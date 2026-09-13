package com.fieldops.analytics.application.service;

import com.fieldops.analytics.api.dto.DailyMetricItemDto;
import com.fieldops.analytics.api.dto.DailyMetricsResponse;
import com.fieldops.analytics.api.dto.TechnicianMetricItemDto;
import com.fieldops.analytics.api.dto.TechnicianMetricsResponse;
import com.fieldops.analytics.domain.model.WorkOrderDailyMetric;
import com.fieldops.analytics.infrastructure.persistence.WorkOrderDailyMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultAnalyticsQueryService implements AnalyticsQueryService {

    private final WorkOrderDailyMetricRepository metricRepository;

    public DefaultAnalyticsQueryService(WorkOrderDailyMetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DailyMetricsResponse getDailyMetrics(LocalDate from, LocalDate to) {
        List<WorkOrderDailyMetric> rawMetrics = metricRepository.findByDateRange(from, to);

        List<DailyMetricItemDto> items = rawMetrics.stream()
                .map(m -> new DailyMetricItemDto(
                        m.getId().getMetricDate(),
                        m.getId().getTechnicianId(),
                        m.getId().getStatus(),
                        m.getOrderCount(),
                        m.getAvgDurationMinutes(),
                        m.getUpdatedAt()
                ))
                .toList();

        long totalOrders = rawMetrics.stream().mapToLong(WorkOrderDailyMetric::getOrderCount).sum();

        BigDecimal weightedDurationSum = BigDecimal.ZERO;
        long completedCount = 0;
        for (WorkOrderDailyMetric m : rawMetrics) {
            if ("COMPLETED".equals(m.getId().getStatus()) && m.getAvgDurationMinutes() != null) {
                weightedDurationSum = weightedDurationSum.add(
                        m.getAvgDurationMinutes().multiply(BigDecimal.valueOf(m.getOrderCount()))
                );
                completedCount += m.getOrderCount();
            }
        }

        BigDecimal overallAvgDuration = completedCount > 0
                ? weightedDurationSum.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP)
                : null;

        return new DailyMetricsResponse(from, to, totalOrders, overallAvgDuration, items);
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianMetricsResponse getTechnicianMetrics() {
        List<WorkOrderDailyMetric> metrics = metricRepository.findAllTechnicianMetrics();

        Map<Long, List<WorkOrderDailyMetric>> byTechnician = new LinkedHashMap<>();
        for (WorkOrderDailyMetric m : metrics) {
            byTechnician.computeIfAbsent(m.getId().getTechnicianId(), k -> new ArrayList<>()).add(m);
        }

        List<TechnicianMetricItemDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<WorkOrderDailyMetric>> entry : byTechnician.entrySet()) {
            Long techId = entry.getKey();
            List<WorkOrderDailyMetric> list = entry.getValue();

            long completed = 0;
            long assigned = 0;
            long inProgress = 0;
            BigDecimal durationSum = BigDecimal.ZERO;

            for (WorkOrderDailyMetric m : list) {
                switch (m.getId().getStatus()) {
                    case "COMPLETED" -> {
                        completed += m.getOrderCount();
                        if (m.getAvgDurationMinutes() != null) {
                            durationSum = durationSum.add(
                                    m.getAvgDurationMinutes().multiply(BigDecimal.valueOf(m.getOrderCount()))
                            );
                        }
                    }
                    case "ASSIGNED" -> assigned += m.getOrderCount();
                    case "IN_PROGRESS" -> inProgress += m.getOrderCount();
                    default -> {}
                }
            }

            BigDecimal avgDuration = completed > 0
                    ? durationSum.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                    : null;

            result.add(new TechnicianMetricItemDto(techId, completed, assigned, inProgress, avgDuration));
        }

        return new TechnicianMetricsResponse(result);
    }
}
