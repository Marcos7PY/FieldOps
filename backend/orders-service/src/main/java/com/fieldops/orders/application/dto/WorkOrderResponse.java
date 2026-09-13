package com.fieldops.orders.application.dto;

import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.Priority;
import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderResponse(
        Long id,
        String code,
        String title,
        String description,
        OrderStatus status,
        Priority priority,
        ClientResponse client,
        Long assignedTechnicianId,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long version,
        List<EvidenceResponse> evidences,
        List<StatusHistoryResponse> statusHistory
) {}
