package com.fieldops.analytics.api.dto;

import java.time.LocalDateTime;

public record RebuildProjectionResponse(
        String status,
        String message,
        LocalDateTime initiatedAt
) {}
