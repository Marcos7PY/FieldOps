package com.fieldops.analytics.api.dto;

import java.time.LocalDateTime;

public record RebuildStatusResponse(
        boolean inProgress,
        LocalDateTime lastRebuiltAt
) {}
