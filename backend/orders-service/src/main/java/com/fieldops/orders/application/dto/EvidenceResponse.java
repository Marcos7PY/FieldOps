package com.fieldops.orders.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EvidenceResponse(
        Long id,
        Long workOrderId,
        String filePath,
        String contentType,
        Long sizeBytes,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime capturedAt,
        LocalDateTime uploadedAt
) {}