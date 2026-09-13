package com.fieldops.orders.application.dto;

import com.fieldops.orders.domain.model.OrderStatus;
import java.time.LocalDateTime;

public record StatusHistoryResponse(
        Long id,
        OrderStatus previousStatus,
        OrderStatus newStatus,
        Long changedBy,
        LocalDateTime changedAt,
        String notes
) {}