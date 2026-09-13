package com.fieldops.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EvidenceMetadata(
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime capturedAt
) {}
