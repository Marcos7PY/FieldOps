package com.fieldops.analytics.api.dto;

import java.util.List;

public record TechnicianMetricsResponse(
        List<TechnicianMetricItemDto> technicians
) {}
