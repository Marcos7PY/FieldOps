package com.fieldops.analytics.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyMetricItemDto(
        LocalDate metricDate,
        Long technicianId,
        String status,
        int orderCount,
        BigDecimal avgDurationMinutes,
        LocalDateTime updatedAt
) {}
