package com.fieldops.analytics.application.service;

import com.fieldops.analytics.api.dto.DailyMetricsResponse;
import com.fieldops.analytics.api.dto.TechnicianMetricsResponse;
import com.fieldops.analytics.domain.model.WorkOrderDailyMetric;
import com.fieldops.analytics.infrastructure.persistence.WorkOrderDailyMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAnalyticsQueryServiceTest {

    @Mock
    private WorkOrderDailyMetricRepository metricRepository;

    private DefaultAnalyticsQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new DefaultAnalyticsQueryService(metricRepository);
    }

    @Test
    void shouldCalculateDailyMetricsCorrectly() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 13);
        LocalDateTime now = LocalDateTime.now();

        List<WorkOrderDailyMetric> metrics = List.of(
                new WorkOrderDailyMetric(LocalDate.of(2026, 9, 10), 42L, "COMPLETED", 2, BigDecimal.valueOf(60.0), now),
                new WorkOrderDailyMetric(LocalDate.of(2026, 9, 11), 42L, "COMPLETED", 1, BigDecimal.valueOf(90.0), now),
                new WorkOrderDailyMetric(LocalDate.of(2026, 9, 12), 43L, "ASSIGNED", 3, null, now)
        );

        when(metricRepository.findByDateRange(from, to)).thenReturn(metrics);

        DailyMetricsResponse response = queryService.getDailyMetrics(from, to);

        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(to);
        assertThat(response.totalOrders()).isEqualTo(6L);
        // (60*2 + 90*1) / 3 = 210 / 3 = 70.00
        assertThat(response.overallAvgDurationMinutes()).isEqualByComparingTo(BigDecimal.valueOf(70.0));
        assertThat(response.metrics()).hasSize(3);
    }

    @Test
    void shouldCalculateTechnicianMetricsCorrectly() {
        LocalDateTime now = LocalDateTime.now();

        List<WorkOrderDailyMetric> metrics = List.of(
                new WorkOrderDailyMetric(LocalDate.of(2026, 9, 10), 42L, "COMPLETED", 2, BigDecimal.valueOf(50.0), now),
                new WorkOrderDailyMetric(LocalDate.of(2026, 9, 11), 42L, "ASSIGNED", 3, null, now),
                new WorkOrderDailyMetric(LocalDate.of(2026, 9, 12), 42L, "IN_PROGRESS", 1, null, now),
                new WorkOrderDailyMetric(LocalDate.of(2026, 9, 10), 43L, "COMPLETED", 1, BigDecimal.valueOf(80.0), now)
        );

        when(metricRepository.findAllTechnicianMetrics()).thenReturn(metrics);

        TechnicianMetricsResponse response = queryService.getTechnicianMetrics();

        assertThat(response.technicians()).hasSize(2);

        var tech42 = response.technicians().stream().filter(t -> t.technicianId().equals(42L)).findFirst().orElseThrow();
        assertThat(tech42.completedOrders()).isEqualTo(2);
        assertThat(tech42.assignedOrders()).isEqualTo(3);
        assertThat(tech42.inProgressOrders()).isEqualTo(1);
        assertThat(tech42.avgDurationMinutes()).isEqualByComparingTo(BigDecimal.valueOf(50.0));

        var tech43 = response.technicians().stream().filter(t -> t.technicianId().equals(43L)).findFirst().orElseThrow();
        assertThat(tech43.completedOrders()).isEqualTo(1);
        assertThat(tech43.assignedOrders()).isEqualTo(0);
        assertThat(tech43.inProgressOrders()).isEqualTo(0);
        assertThat(tech43.avgDurationMinutes()).isEqualByComparingTo(BigDecimal.valueOf(80.0));
    }
}
