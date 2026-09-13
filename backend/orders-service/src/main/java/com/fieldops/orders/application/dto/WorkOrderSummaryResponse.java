package com.fieldops.orders.application.dto;

import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.Priority;
import java.time.LocalDateTime;

public record WorkOrderSummaryResponse(
        Long id,
        String code,
        String title,
        OrderStatus status,
        Priority priority,
        Long clientId,
        String clientName,
        Long assignedTechnicianId,
        LocalDateTime createdAt,
        LocalDateTime scheduledAt,
        Long version
) {}
