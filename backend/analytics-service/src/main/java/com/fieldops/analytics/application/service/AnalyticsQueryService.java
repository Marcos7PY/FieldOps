package com.fieldops.analytics.application.service;

import com.fieldops.analytics.api.dto.DailyMetricsResponse;
import com.fieldops.analytics.api.dto.TechnicianMetricsResponse;

import java.time.LocalDate;

public interface AnalyticsQueryService {

    DailyMetricsResponse getDailyMetrics(LocalDate from, LocalDate to);

    TechnicianMetricsResponse getTechnicianMetrics();
}
