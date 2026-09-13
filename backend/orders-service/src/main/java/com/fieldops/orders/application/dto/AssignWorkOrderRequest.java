package com.fieldops.orders.application.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AssignWorkOrderRequest(
        @NotNull Long technicianId,
        LocalDateTime scheduledAt
) {}