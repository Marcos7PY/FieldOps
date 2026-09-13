package com.fieldops.orders.application.dto;

import com.fieldops.orders.domain.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateWorkOrderRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 1000) String description,
        @NotNull Priority priority,
        @NotNull Long clientId,
        Long assignedTechnicianId,
        LocalDateTime scheduledAt
) {}
