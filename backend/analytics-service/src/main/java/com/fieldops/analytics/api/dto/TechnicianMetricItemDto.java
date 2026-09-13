package com.fieldops.analytics.api.dto;

import java.math.BigDecimal;

public record TechnicianMetricItemDto(
        Long technicianId,
        long completedOrders,
        long assignedOrders,
        long inProgressOrders,
        BigDecimal avgDurationMinutes
) {}
