package com.fieldops.analytics.infrastructure.persistence;

import com.fieldops.analytics.domain.model.WorkOrderDailyMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class WorkOrderDailyMetricRepositoryTest {

    @Autowired
    private WorkOrderDailyMetricRepository metricRepository;

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        metricRepository.truncateAll();
    }

    @Test
    void shouldInsertAndMergeMetricsCorrectly() {
        LocalDate date = LocalDate.of(2026, 9, 13);
        LocalDateTime now = LocalDateTime.now();

        // 1st insert: count 1, duration 60
        metricRepository.upsertMetric(date, 42L, "COMPLETED", 1, BigDecimal.valueOf(60.0), now);

        List<WorkOrderDailyMetric> list1 = metricRepository.findByDateRange(date, date);
        assertThat(list1).hasSize(1);
        assertThat(list1.get(0).getOrderCount()).isEqualTo(1);
        assertThat(list1.get(0).getAvgDurationMinutes()).isEqualByComparingTo(BigDecimal.valueOf(60.0));

        // 2nd insert for same key: count 1, duration 120 -> expected count 2, avg 90.0
        metricRepository.upsertMetric(date, 42L, "COMPLETED", 1, BigDecimal.valueOf(120.0), now.plusMinutes(5));
        entityManager.clear();

        List<WorkOrderDailyMetric> list2 = metricRepository.findByDateRange(date, date);
        assertThat(list2).hasSize(1);
        assertThat(list2.get(0).getOrderCount()).isEqualTo(2);
        assertThat(list2.get(0).getAvgDurationMinutes()).isEqualByComparingTo(BigDecimal.valueOf(90.0));
    }

    @Test
    void shouldCalculateWeightedAverageCorrectlyAfterThreeEvents() {
        LocalDate date = LocalDate.of(2026, 9, 13);
        LocalDateTime now = LocalDateTime.now();

        // 3 consecutive events with durations 10, 20, and 60 minutes
        // Expected weighted average: (10 + 20 + 60) / 3 = 30.00
        metricRepository.upsertMetric(date, 99L, "COMPLETED", 1, BigDecimal.valueOf(10.0), now);
        entityManager.clear();

        metricRepository.upsertMetric(date, 99L, "COMPLETED", 1, BigDecimal.valueOf(20.0), now.plusMinutes(1));
        entityManager.clear();

        metricRepository.upsertMetric(date, 99L, "COMPLETED", 1, BigDecimal.valueOf(60.0), now.plusMinutes(2));
        entityManager.clear();

        List<WorkOrderDailyMetric> list = metricRepository.findByDateRange(date, date);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getOrderCount()).isEqualTo(3);
        assertThat(list.get(0).getAvgDurationMinutes()).isEqualByComparingTo(new BigDecimal("30.00"));
    }
}
