package com.fieldops.orders.application.dto;

import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.Priority;

import java.math.BigDecimal;
import java.util.Map;

public record WorkOrderMetricsResponse(
        long totalOrders,
        Map<OrderStatus, Long> ordersByStatus,
        Map<Priority, Long> ordersByPriority,
        BigDecimal avgDurationMinutes
) {}