package com.fieldops.analytics.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyMetricsResponse(
        LocalDate from,
        LocalDate to,
        long totalOrders,
        BigDecimal overallAvgDurationMinutes,
        List<DailyMetricItemDto> metrics
) {}
