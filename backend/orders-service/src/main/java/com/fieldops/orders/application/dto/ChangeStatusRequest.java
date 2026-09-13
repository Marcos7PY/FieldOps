package com.fieldops.orders.application.dto;

import com.fieldops.orders.domain.model.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(
        @NotNull OrderStatus newStatus,
        @Size(max = 500) String notes
) {}